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
		if (requireNonNull(rounds).isEmpty()) {
			throw new IllegalArgumentException("Empty rounds");
		}
		this.rounds = rounds;

		if (requireNonNull(graph).isEmpty()) {
			throw new IllegalArgumentException("Empty graph/map");
		}
		this.graph = graph;

		if (mrX.colour.isDetective()) {
			throw new IllegalArgumentException("MrX should be BLACK");
		}

		ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
		configurations.add(requireNonNull(mrX));
		configurations.add(requireNonNull(firstDetective));
		for (PlayerConfiguration config : restOfTheDetectives){
			configurations.add(requireNonNull(config));
		}

		Set<Integer> locations = new HashSet<>();
		Set<Colour> colours = new HashSet<>();
		for (PlayerConfiguration config : configurations) {
			if (locations.contains(config.location)){
				throw new IllegalArgumentException("Duplicate player location");
			}
			locations.add(config.location);

			if (colours.contains(config.colour)){
				throw new IllegalArgumentException("Duplicate player colour");
			}
			colours.add(config.colour);

			Set<Ticket> keys = config.tickets.keySet();
			List<Ticket> tickets = new ArrayList<Ticket>(List.of(BUS, DOUBLE, SECRET, TAXI, UNDERGROUND));
			if (!(keys.containsAll(tickets))) {
				throw new IllegalArgumentException("PlayerConfiguration missing one or more ticket type.");
			}

			if (config.colour.isDetective() && (config.tickets.get(DOUBLE) > 0 || config.tickets.get(SECRET) > 0)){
				throw new IllegalArgumentException("Detective has DOUBLE or SECRET tickets");
			}
		}

		this.players = new ArrayList<ScotlandYardPlayer>();
		for (PlayerConfiguration config : configurations) {
			players.add(new ScotlandYardPlayer(config.player, config.colour, config.location, config.tickets));
		}
	}

	private Boolean locationOccupiedByDetective(Integer location) {
		for (ScotlandYardPlayer player : players) {
			if (player.location() == location && player.colour().isDetective()) {
				return true;
			}
		}
		return false;
	}

	private Set<Move> doubleMoves(Colour colour, Integer location, Ticket prevTicket, Map<Transport,Ticket> ticketMap) {
		HashSet<Move> moves = new HashSet<Move>();
		if (colour.isMrX() && getPlayerTickets(colour, DOUBLE).get() > 0) {
			for (Edge<Integer,Transport> edge : graph.getEdgesFrom(graph.getNode(location))) {
				Transport transport = edge.data();
				Integer destination = edge.destination().value();
				if (!locationOccupiedByDetective(destination) && currentRound < getRounds().size() - 1) {
					Integer minimumTicketCount = (ticketMap.get(transport) == prevTicket) ? 1 : 0;
					if (getPlayerTickets(colour, ticketMap.get(transport)).get() > minimumTicketCount) {
						moves.add(new DoubleMove(colour, prevTicket, location, ticketMap.get(transport), destination));
					}
					minimumTicketCount = (prevTicket == SECRET) ? 1 : 0;
					if (getPlayerTickets(colour, SECRET).get() > 0 && transport != Transport.FERRY) {
						moves.add(new DoubleMove(colour, prevTicket, location, SECRET, destination));
					}
				}
			}
		}
		return moves;
	}

	private Set<Move> validMoves(Colour colour) {
		HashSet<Move> moves = new HashSet<Move>();
		Integer location = getPlayer(colour).get().location();
		Map<Transport,Ticket> ticketMap = Map.of(Transport.BUS, BUS,
									Transport.TAXI, TAXI,
									Transport.UNDERGROUND, UNDERGROUND,
									Transport.FERRY, SECRET);
		for (Edge<Integer,Transport> edge : graph.getEdgesFrom(graph.getNode(location))) {
			Transport transport = edge.data();
			Integer destination = edge.destination().value();
			if (!locationOccupiedByDetective(destination)) {
				if (getPlayerTickets(colour, ticketMap.get(transport)).get() > 0) {
					moves.add(new TicketMove(colour, ticketMap.get(transport), destination));
					moves.addAll(doubleMoves(colour, destination, ticketMap.get(transport), ticketMap));
				}
				if (getPlayerTickets(colour, SECRET).get() > 0 && transport != Transport.FERRY) {
					moves.add(new TicketMove(colour, SECRET, destination));
					moves.addAll(doubleMoves(colour, destination, SECRET, ticketMap));
				}
			}
		}
		if (moves.isEmpty()){
			moves.add(new PassMove(colour));
		}
		return moves;
	}

	private Optional<ScotlandYardPlayer> getPlayer(Colour colour) {
		for (ScotlandYardPlayer player : players) {
			if (player.colour() == colour) {
				return Optional.of(player);
			}
		}
		return Optional.empty();
	}

	private void notifyOnMoveMade(Move move) {
		isGameOver();
		for (Spectator spectator : spectators) {
			spectator.onMoveMade(this, move);
		}
	}

	private void notifyOnRoundStarted() {
		if (getRounds().get(currentRound)) {
			this.prevMrXLocation = players.get(0).location();
		}
		currentRound += 1;
		for (Spectator spectator : spectators) {
			spectator.onRoundStarted(this, currentRound);
		}
	}


	@Override
	public void startRotate() {
		if (isGameOver()) {
			throw new IllegalStateException("Game is over, cannot start new rotation.");
		}
		ScotlandYardPlayer player = players.get(0);
		player.player().makeMove(this, player.location(), validMoves(player.colour()), this);
	}

	@Override
	public void accept(Move m) {
		if (!validMoves(getCurrentPlayer()).contains(requireNonNull(m))){
			throw new IllegalArgumentException("Move not in MOVES");
		}
		prevPlayer = currentPlayer;
		currentPlayer += 1;
		if (currentPlayer == players.size()) {
			currentPlayer = 0;
		}
		m.visit(this);
		if (isGameOver()) {
			for (Spectator spectator : spectators) {
				spectator.onGameOver(this, winners);
			}
		}
		else if (players.get(currentPlayer).isDetective()) {
			ScotlandYardPlayer player = players.get(currentPlayer);
			player.player().makeMove(this, player.location(), validMoves(player.colour()), this);
		}
		else {
			for (Spectator spectator : spectators) {
				spectator.onRotationComplete(this);
			}
		}
	}

	@Override
	public void visit(DoubleMove move) {
		Colour player = players.get(prevPlayer).colour();
		Integer destination = (rounds.get(currentRound)) ? move.firstMove().destination() : prevMrXLocation;
		TicketMove firstMove = new TicketMove(player, move.firstMove().ticket(), destination);
		destination = (rounds.get(currentRound + 1)) ? move.secondMove().destination() : firstMove.destination();
		TicketMove secondMove = new TicketMove(player, move.secondMove().ticket(), destination);

		players.get(0).removeTicket(DOUBLE);
		notifyOnMoveMade(new DoubleMove(player, firstMove, secondMove));
		move.firstMove().visit(this);
		move.secondMove().visit(this);
	}

	@Override
	public void visit(PassMove move) {
		if (players.get(prevPlayer).isMrX()) {
			notifyOnRoundStarted();
		}
		notifyOnMoveMade(move);
	}

	@Override
	public void visit(TicketMove move) {
		ScotlandYardPlayer player = players.get(prevPlayer);
		player.removeTicket(move.ticket());
		player.location(move.destination());

		if (player.isMrX()) {
			notifyOnRoundStarted();
		}
		else { players.get(0).addTicket(move.ticket()); }
		Integer destination = (player.isMrX()) ? prevMrXLocation : player.location();
		notifyOnMoveMade(new TicketMove(player.colour(), move.ticket(), destination));
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		if (spectators.contains(requireNonNull(spectator))) {
			throw new IllegalArgumentException("Spectators already contains SPECTATOR");
		}
		spectators.add(spectator);
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		if (!spectators.contains(requireNonNull(spectator))) {
			throw new IllegalArgumentException("Spectators list does not contain SPECTATOR");
		}
		spectators.remove(spectator);
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableList(spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		List<Colour> colours = new ArrayList<Colour>();
		for (ScotlandYardPlayer player : players){
			colours.add(player.colour());
		}
		return Collections.unmodifiableList(colours);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		return Collections.unmodifiableSet(winners);
	}

	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		if (colour.isMrX()){
			return Optional.of(prevMrXLocation);
		}
		if (!getPlayer(colour).equals(Optional.empty())) {
			return Optional.of(getPlayer(colour).get().location());
		}
		return Optional.empty();
	}

	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		if (!getPlayer(colour).equals(Optional.empty())) {
			return Optional.of(getPlayer(colour).get().tickets().get(ticket));
		}
		return Optional.empty();
	}

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
			addDetectivesToWinners();
			return true;
		}
		if (prevPlayer == players.size() - 1) {
			ScotlandYardPlayer mrX = players.get(0);
			boolean mrXHasTickets = mrX.hasTickets(BUS) || mrX.hasTickets(TAXI) || mrX.hasTickets(UNDERGROUND) || mrX.hasTickets(SECRET) || mrX.hasTickets(DOUBLE);
			if (!mrXHasTickets) {
				addDetectivesToWinners();
				return true;
			}
			if (validMoves(players.get(0).colour()).size() == 1 && mrXHasTickets) {
				addDetectivesToWinners();
				return true;
			}
		}

		// WINNERS: Mr.X
		// CASE: Mr.X is not captured in any round
		// CASE: All detectives are stuck
		if (prevPlayer == players.size() - 1 && currentRound == rounds.size()) {
			winners.add(players.get(0).colour());
			return true;
		}
		allDetectivesStuck: {
			for (ScotlandYardPlayer player : players) {
				if (player.isDetective() && (player.hasTickets(BUS) || player.hasTickets(TAXI) || player.hasTickets(UNDERGROUND))) {
					break allDetectivesStuck;
				}
			}
			winners.add(players.get(0).colour());
			return true;
		}
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
