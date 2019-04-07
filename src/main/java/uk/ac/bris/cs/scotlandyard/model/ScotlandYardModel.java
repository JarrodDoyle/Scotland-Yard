package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.DOUBLE;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.SECRET;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.BUS;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.TAXI;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.UNDERGROUND;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Graph;

public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {
	private final List<Boolean> rounds;
	private final Graph<Integer, Transport> graph;
	private List<ScotlandYardPlayer> players;
	private Integer currentPlayer = 0;
	private Integer prevPlayer = 0;
	private Integer currentRound = NOT_STARTED;
	private Integer prevMrXLocation = 0;
	private Set<Move> moves = new HashSet<Move>();
	private Set<Colour> winners = new HashSet<Colour>();
	private List<Spectator> spectators = new ArrayList<Spectator>();

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {
		// If passed rounds is null or empty, throw the appropriate exception
		if (requireNonNull(rounds).isEmpty()) {
			throw new IllegalArgumentException("Empty rounds");
		}
		this.rounds = rounds;

		// If passed graph is null or empty, throw the appropriate exception
		if (requireNonNull(graph).isEmpty()) {
			throw new IllegalArgumentException("Empty graph/map");
		}
		this.graph = graph;

		// Make sure the colour given for mrX is black
		if (mrX.colour.isDetective()) {
			throw new IllegalArgumentException("MrX should be BLACK");
		}

		// Add MrX and all of the detectives to a list, checking that they're
		// not null as we go
		ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
		configurations.add(requireNonNull(mrX));
		configurations.add(requireNonNull(firstDetective));
		for (PlayerConfiguration config : restOfTheDetectives){
			configurations.add(requireNonNull(config));
		}

		Set<Integer> locations = new HashSet<>();
		Set<Colour> colours = new HashSet<>();
		for (PlayerConfiguration config : configurations) {
			// Add each players location and colour to a set, throwing an error
			// if a duplicate is found.
			if (locations.contains(config.location)){
				throw new IllegalArgumentException("Duplicate player location");
			}
			locations.add(config.location);

			if (colours.contains(config.colour)){
				throw new IllegalArgumentException("Duplicate player colour");
			}
			colours.add(config.colour);

			// Make sure each player has appropriate tickets
			Set<Ticket> keys = config.tickets.keySet();
			List<Ticket> tickets = new ArrayList<Ticket>(List.of(BUS, DOUBLE, SECRET, TAXI, UNDERGROUND));
			if (!(keys.containsAll(tickets))) {
				throw new IllegalArgumentException("PlayerConfiguration missing one or more ticket type.");
			}
			if (config.colour.isDetective() && (config.tickets.get(DOUBLE) > 0 || config.tickets.get(SECRET) > 0)){
				throw new IllegalArgumentException("Detective has DOUBLE or SECRET tickets");
			}
		}

		// Make each player config a ScotlandYardPlayer to ensure mutability
		// without mutating the supplied configs
		this.players = new ArrayList<ScotlandYardPlayer>();
		for (PlayerConfiguration config : configurations) {
			players.add(new ScotlandYardPlayer(config.player, config.colour, config.location, config.tickets));
		}
	}

	// Returns whether a detective is in the supplied location
	private Boolean locationOccupiedByDetective(Integer location) {
		for (ScotlandYardPlayer player : players) {
			if (player.location() == location && player.colour().isDetective()) {
				return true;
			}
		}
		return false;
	}

	// Generates a set of valid double moves given a first move
	private Set<Move> doubleMoves(Colour colour, Integer location, Ticket prevTicket, Map<Transport,Ticket> ticketMap) {
		HashSet<Move> moves = new HashSet<Move>();
		// Check if the player can make double moves before generating anything
		if (colour.isMrX() && getPlayerTickets(colour, DOUBLE).get() > 0 && currentRound < getRounds().size() - 1) {
			// Iterate over the list of edges from the current position of the player
			// Each of these edges is a potential move that can be made
			for (Edge<Integer,Transport> edge : graph.getEdgesFrom(graph.getNode(location))) {
				Transport transport = edge.data();
				Integer destination = edge.destination().value();
				if (!locationOccupiedByDetective(destination)) {
					// Ticket used in first half of double move is not actually
					// subtracted from the players ticket count. If a ticket type
					// used in the first half will be used in the second, make
					// sure the player has enough.
					// A map is used to get the ticket type needed for each
					// transport type.
					Integer minimumTicketCount = (ticketMap.get(transport) == prevTicket) ? 1 : 0;
					if (getPlayerTickets(colour, ticketMap.get(transport)).get() > minimumTicketCount) {
						moves.add(new DoubleMove(colour, prevTicket, location, ticketMap.get(transport), destination));
					}
					// Don't forget the possibility of using secret tickets. They
					// can be used for any type of transport.
					minimumTicketCount = (prevTicket == SECRET) ? 1 : 0;
					if (getPlayerTickets(colour, SECRET).get() > 0 && transport != Transport.FERRY) {
						moves.add(new DoubleMove(colour, prevTicket, location, SECRET, destination));
					}
				}
			}
		}
		return moves;
	}

	// Generates the set of valid moves for the given colour. Includes doubles.
	private Set<Move> validMoves(Colour colour) {
		HashSet<Move> moves = new HashSet<Move>();
		Integer location = getPlayer(colour).get().location();
		// Map of transport type to ticket type.
		Map<Transport,Ticket> ticketMap = Map.of(Transport.BUS, BUS,
									Transport.TAXI, TAXI,
									Transport.UNDERGROUND, UNDERGROUND,
									Transport.FERRY, SECRET);
		// Iterate over the list of edges from the current position of the player
		// Each of these edges is a potential move that can be made
		for (Edge<Integer,Transport> edge : graph.getEdgesFrom(graph.getNode(location))) {
			Transport transport = edge.data();
			Integer destination = edge.destination().value();
			if (!locationOccupiedByDetective(destination)) {
				// Map generated earlier is used to get the appropriate ticket
				// for the transport type of the move.
				// Don't forget to generate the potential double moves from the
				// position of the first move.
				if (getPlayerTickets(colour, ticketMap.get(transport)).get() > 0) {
					moves.add(new TicketMove(colour, ticketMap.get(transport), destination));
					moves.addAll(doubleMoves(colour, destination, ticketMap.get(transport), ticketMap));
				}
				// Don't forget the possibility of using secret tickets. They
				// can be used for any type of transport.
				if (getPlayerTickets(colour, SECRET).get() > 0 && transport != Transport.FERRY) {
					moves.add(new TicketMove(colour, SECRET, destination));
					moves.addAll(doubleMoves(colour, destination, SECRET, ticketMap));
				}
			}
		}
		// If no possible moves are generated, add a passmove
		if (moves.isEmpty()){
			moves.add(new PassMove(colour));
		}
		return moves;
	}

	// Returns the ScotlandYardPlayer with given colour is it exists
	private Optional<ScotlandYardPlayer> getPlayer(Colour colour) {
		for (ScotlandYardPlayer player : players) {
			if (player.colour() == colour) {
				return Optional.of(player);
			}
		}
		return Optional.empty();
	}

	// Notify all spectators that a move has been made
	private void notifyOnMoveMade(Move move) {
		// isGameOver() is ran to ensure the winners set is updated.
		isGameOver();
		for (Spectator spectator : spectators) {
			spectator.onMoveMade(this, move);
		}
	}

	// Notify all spectators that a new round has started
	private void notifyOnRoundStarted() {
		// Check if the current round is a reveal round and update mrX position
		if (getRounds().get(currentRound)) {
			this.prevMrXLocation = players.get(0).location();
		}
		currentRound += 1;
		for (Spectator spectator : spectators) {
			spectator.onRoundStarted(this, currentRound);
		}
	}


	@Override
	// Start player rotation
	public void startRotate() {
		// Game should not be over at the start of a rotation
		if (isGameOver()) {
			throw new IllegalStateException("Game is over, cannot start new rotation.");
		}
		// Generate set of validmoves for MrX and ask him to choose one.
		ScotlandYardPlayer player = players.get(0);
		player.player().makeMove(this, player.location(), validMoves(player.colour()), this);
	}

	@Override
	// Player has chosen a move
	public void accept(Move m) {
		// Make sure the move given is valid and not null
		if (!validMoves(getCurrentPlayer()).contains(requireNonNull(m))){
			throw new IllegalArgumentException("Move not in MOVES");
		}
		// Uppdate prevPlayer and currentPlayer trackers
		prevPlayer = currentPlayer;
		currentPlayer += 1;
		if (currentPlayer == players.size()) {
			currentPlayer = 0;
		}
		// Make the move
		m.visit(this);
		// If after making a move the game is over, stop rotating and notify spectators
		if (isGameOver()) {
			for (Spectator spectator : spectators) {
				spectator.onGameOver(this, winners);
			}
		}
		// Continue rotation
		else if (players.get(currentPlayer).isDetective()) {
			ScotlandYardPlayer player = players.get(currentPlayer);
			player.player().makeMove(this, player.location(), validMoves(player.colour()), this);
		}
		// Rotation ended.
		else {
			for (Spectator spectator : spectators) {
				spectator.onRotationComplete(this);
			}
		}
	}

	@Override
	// Make a double move
	public void visit(DoubleMove move) {
		Colour player = players.get(prevPlayer).colour();
		// Create the doublemove to be shown to spectators.
		// Make sure destinations are only shown if the round they are played in
		// is a reveal round. Otherwise just show the previous known location of MrX.
		Integer destination = (rounds.get(currentRound)) ? move.firstMove().destination() : prevMrXLocation;
		TicketMove firstMove = new TicketMove(player, move.firstMove().ticket(), destination);
		destination = (rounds.get(currentRound + 1)) ? move.secondMove().destination() : firstMove.destination();
		TicketMove secondMove = new TicketMove(player, move.secondMove().ticket(), destination);

		players.get(prevPlayer).removeTicket(DOUBLE);
		// Send notification to spectators and make the moves in the doublemove
		notifyOnMoveMade(new DoubleMove(player, firstMove, secondMove));
		move.firstMove().visit(this);
		move.secondMove().visit(this);
	}

	@Override
	public void visit(PassMove move) {
		// If the player making the move is MrX, start a new round.
		if (players.get(prevPlayer).isMrX()) {
			notifyOnRoundStarted();
		}
		notifyOnMoveMade(move);
	}

	@Override
	// Do a standard move
	public void visit(TicketMove move) {
		ScotlandYardPlayer player = players.get(prevPlayer);
		player.removeTicket(move.ticket());
		player.location(move.destination());

		// If the player making the move is MrX, start a new round.
		if (player.isMrX()) {
			notifyOnRoundStarted();
		}
		// Give MrX the ticket used
		else {
			players.get(0).addTicket(move.ticket());
		}
		Integer destination = (player.isMrX()) ? prevMrXLocation : player.location();
		notifyOnMoveMade(new TicketMove(player.colour(), move.ticket(), destination));
	}

	@Override
	// Register a spectator to the view
	public void registerSpectator(Spectator spectator) {
		// Make sure the spectator is not already registered and is not null
		if (spectators.contains(requireNonNull(spectator))) {
			throw new IllegalArgumentException("Spectators already contains SPECTATOR");
		}
		spectators.add(spectator);
	}

	@Override
	// Remove a spectator from the view
	public void unregisterSpectator(Spectator spectator) {
		// Make sure the spectator is registered and is not null
		if (!spectators.contains(requireNonNull(spectator))) {
			throw new IllegalArgumentException("Spectators list does not contain SPECTATOR");
		}
		spectators.remove(spectator);
	}

	@Override
	// Return an immutable list of the spectators
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableList(spectators);
	}

	@Override
	// Return an immutable list of the player colours in the game
	public List<Colour> getPlayers() {
		List<Colour> colours = new ArrayList<Colour>();
		for (ScotlandYardPlayer player : players){
			colours.add(player.colour());
		}
		return Collections.unmodifiableList(colours);
	}

	@Override
	// Return an immutable list of the players that won.
	public Set<Colour> getWinningPlayers() {
		return Collections.unmodifiableSet(winners);
	}

	@Override
	// Return the previously known location of a player if they exist in the game
	public Optional<Integer> getPlayerLocation(Colour colour) {
		if (colour.isMrX()){
			return Optional.of(prevMrXLocation);
		}
		// If the ScotlandYardPlayer associated with the colour exists, return its location
		if (!getPlayer(colour).equals(Optional.empty())) {
			return Optional.of(getPlayer(colour).get().location());
		}
		return Optional.empty();
	}

	@Override
	// Return the map of player tickets if the given player exists
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		if (!getPlayer(colour).equals(Optional.empty())) {
			return Optional.of(getPlayer(colour).get().tickets().get(ticket));
		}
		return Optional.empty();
	}

	// Adds all the detectives to the winners set
	private void addDetectivesToWinners() {
		for (ScotlandYardPlayer player : players) {
			if (player.isDetective()) {
				winners.add(player.colour());
			}
		}
	}

	@Override
	public boolean isGameOver() {
		// WINNERS: Detectives
		// CASE: Mr.X is captured
		// CASE: Mr.X is cornered
		// CASE: Mr.X cannot move
		if (locationOccupiedByDetective(players.get(0).location())){
			// MrX has been captured!
			addDetectivesToWinners();
			return true;
		}
		if (prevPlayer == players.size() - 1) {
			ScotlandYardPlayer mrX = players.get(0);
			boolean mrXHasTickets = mrX.hasTickets(BUS) || mrX.hasTickets(TAXI) || mrX.hasTickets(UNDERGROUND) || mrX.hasTickets(SECRET) || mrX.hasTickets(DOUBLE);
			// MrX cannot move!
			if (!mrXHasTickets) {
				addDetectivesToWinners();
				return true;
			}
			// MrX has been cornered by the detectives!
			if (validMoves(players.get(0).colour()).size() == 1 && mrXHasTickets) {
				addDetectivesToWinners();
				return true;
			}
		}

		// WINNERS: Mr.X
		// CASE: Mr.X is not captured in any round
		// CASE: All detectives are stuck
		if (prevPlayer == players.size() - 1 && currentRound == rounds.size()) {
			// MrX has evaded capture for the entire game!
			winners.add(players.get(0).colour());
			return true;
		}
		allDetectivesStuck: {
			for (ScotlandYardPlayer player : players) {
				if (player.isDetective() && (player.hasTickets(BUS) || player.hasTickets(TAXI) || player.hasTickets(UNDERGROUND))) {
					break allDetectivesStuck;
				}
			}
			// All of the detectives have got themselves stuck
			winners.add(players.get(0).colour());
			return true;
		}
		// No one has won
		return false;
	}

	@Override
	public Colour getCurrentPlayer() {
		return players.get(currentPlayer).colour();
	}

	@Override
	public int getCurrentRound() {
		return currentRound;
	}

	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		return new ImmutableGraph<Integer, Transport>(graph);
	}
}
