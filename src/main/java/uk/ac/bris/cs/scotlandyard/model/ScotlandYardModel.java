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
	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;
	private List<ScotlandYardPlayer> players;
	private Integer currentPlayer;
	private Integer prevPlayer;
	private Integer currentRound;
	private Integer prevMrXLocation;
	private Set<Move> moves;
	private Set<Colour> winners;
	private List<Spectator> spectators;

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

			if (config.colour.isDetective()) {
				if (config.tickets.get(DOUBLE) > 0){
					throw new IllegalArgumentException("Detective has DOUBLE");
				}
				if (config.tickets.get(SECRET) > 0){
					throw new IllegalArgumentException("Detective has SECRET");
				}
			}
		}

		this.players = new ArrayList<ScotlandYardPlayer>();
		for (PlayerConfiguration config : configurations) {
			this.players.add(new ScotlandYardPlayer(config.player, config.colour, config.location, config.tickets));
		}
		this.currentPlayer = 0;
		this.prevPlayer = 0;
		this.prevMrXLocation = 0;
		this.currentRound = 0;
		this.winners = new HashSet<Colour>();
		this.spectators = new ArrayList<Spectator>();
	}

	private Boolean locationOccupiedByDetective(Integer location) {
		for (ScotlandYardPlayer player : this.players) {
			if (player.location() == location && player.colour().isDetective()) {
				return true;
			}
		}
		return false;
	}

	private Set<Move> doubleMoves(Colour colour, Integer location, Ticket prevTicket, Map<Transport,Ticket> ticketMap) {
		HashSet<Move> moves = new HashSet<Move>();
		if (colour.isMrX() && getPlayerTickets(colour, DOUBLE).get() > 0) {
			Collection<Edge<Integer,Transport>> edges = this.graph.getEdgesFrom(this.graph.getNode(location));
			for (Edge<Integer,Transport> edge : edges) {
				Transport transport = edge.data();
				Integer destination = edge.destination().value();
				if (!locationOccupiedByDetective(destination) && this.currentRound < this.getRounds().size() - 1) {
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
		Collection<Edge<Integer,Transport>> edges = this.graph.getEdgesFrom(this.graph.getNode(location));
		Map<Transport,Ticket> ticketMap = Map.of(Transport.BUS, BUS,
									Transport.TAXI, TAXI,
									Transport.UNDERGROUND, UNDERGROUND,
									Transport.FERRY, SECRET);
		for (Edge<Integer,Transport> edge : edges) {
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
		for (ScotlandYardPlayer player : this.players) {
			if (player.colour() == colour){
				return Optional.of(player);
			}
		}
		return Optional.empty();
	}

	private void notifyOnGameOver() {
		for (Spectator spectator : this.spectators) {
			spectator.onGameOver(this, this.winners);
		}
	}

	private void notifyOnMoveMade(Move move) {
		isGameOver();
		for (Spectator spectator : this.spectators) {
			spectator.onMoveMade(this, move);
		}
	}

	private void notifyOnRotationComplete() {
		for (Spectator spectator : this.spectators) {
			spectator.onRotationComplete(this);
		}
	}
	private void notifyOnRoundStarted() {
		this.currentRound += 1;
		for (Spectator spectator : this.spectators) {
			spectator.onRoundStarted(this, this.currentRound);
		}
	}

	private void updateMrXLocation() {
		if (this.getRounds().get(this.currentRound)) {
			this.prevMrXLocation = this.players.get(0).location();
		}
	}

	@Override
	public void startRotate() {
		if (isGameOver()) {
			throw new IllegalStateException("Game is over, cannot start new rotation.");
		}
		this.currentPlayer = 0;
		boolean gameOver = false;
		for (int i=0; i < this.players.size(); i++) {
			if (i == this.currentPlayer) {
				ScotlandYardPlayer player = this.players.get(i);
				this.moves = validMoves(player.colour());
				player.player().makeMove(this, player.location(), this.moves, this);
				if (isGameOver()) {
					gameOver = true;
					notifyOnGameOver();
					break;
				}
			}
		}
		if (!gameOver && this.prevPlayer == this.players.size() - 1){
			notifyOnRotationComplete();
		}
	}

	@Override
	public void accept(Move m) {
		if (!this.moves.contains(requireNonNull(m))){
			throw new IllegalArgumentException("Move not in MOVES");
		}
		this.prevPlayer = this.currentPlayer;
		this.currentPlayer += 1;
		if (this.currentPlayer == this.players.size()) {
			this.currentPlayer = 0;
		}
		m.visit(this);
	}

	@Override
	public void visit(DoubleMove move) {
		Colour player = this.players.get(this.prevPlayer).colour();
		Integer destination = (this.rounds.get(this.currentRound)) ? move.firstMove().destination() : this.prevMrXLocation;
		TicketMove firstMove = new TicketMove(player, move.firstMove().ticket(), destination);
		destination = (this.rounds.get(this.currentRound + 1)) ? move.secondMove().destination() : firstMove.destination();
		TicketMove secondMove = new TicketMove(player, move.secondMove().ticket(), destination);

		this.players.get(0).removeTicket(DOUBLE);
		notifyOnMoveMade(new DoubleMove(player, firstMove, secondMove));
		move.firstMove().visit(this);
		move.secondMove().visit(this);
	}

	@Override
	public void visit(PassMove move) {
		if (this.players.get(this.prevPlayer).isMrX()) {
			updateMrXLocation();
			notifyOnRoundStarted();
		}
		notifyOnMoveMade(move);
	}

	@Override
	public void visit(TicketMove move) {
		ScotlandYardPlayer player = this.players.get(this.prevPlayer);
		player.removeTicket(move.ticket());
		player.location(move.destination());

		if (player.isMrX()) {
			updateMrXLocation();
			notifyOnRoundStarted();
		}
		else { this.players.get(0).addTicket(move.ticket()); }
		Integer destination = (player.isMrX()) ? this.prevMrXLocation : player.location();
		notifyOnMoveMade(new TicketMove(player.colour(), move.ticket(), destination));
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		if (this.spectators.contains(requireNonNull(spectator))) {
			throw new IllegalArgumentException("Spectators already contains SPECTATOR");
		}
		this.spectators.add(spectator);
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		if (!this.spectators.contains(requireNonNull(spectator))) {
			throw new IllegalArgumentException("Spectators list does not contain SPECTATOR");
		}
		this.spectators.remove(spectator);
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableList(this.spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		List<Colour> colours = new ArrayList<Colour>();
		for (ScotlandYardPlayer player : this.players){
			colours.add(player.colour());
		}
		return Collections.unmodifiableList(colours);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		return Collections.unmodifiableSet(this.winners);
	}

	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		if (colour.isMrX()){
			return Optional.of(this.prevMrXLocation);
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
		for (ScotlandYardPlayer player : this.players) {
			if (player.isDetective()) {
				this.winners.add(player.colour());
			}
		}
	}

	@Override
	public boolean isGameOver() {
		// WINNERS: Detectives
		// CASE: Mr.X is captured
		// CASE: Mr.X is cornered
		// CASE: Mr.X cannot move
		if (locationOccupiedByDetective(this.players.get(0).location())){
			addDetectivesToWinners();
			return true;
		}
		if (this.prevPlayer == this.players.size() - 1) {
			ScotlandYardPlayer mrX = this.players.get(0);
			boolean mrXHasTickets = mrX.hasTickets(BUS) || mrX.hasTickets(TAXI) || mrX.hasTickets(UNDERGROUND) || mrX.hasTickets(SECRET) || mrX.hasTickets(DOUBLE);
			if (!mrXHasTickets) {
				addDetectivesToWinners();
				return true;
			}
			if (validMoves(this.players.get(0).colour()).size() == 1 && mrXHasTickets) {
				addDetectivesToWinners();
				return true;
			}
		}

		// WINNERS: Mr.X
		// CASE: Mr.X is not captured in any round
		// CASE: All detectives are stuck
		if (this.prevPlayer == this.players.size() - 1 && this.currentRound == this.rounds.size()) {
			this.winners.add(this.players.get(0).colour());
			return true;
		}
		boolean allDetectivesStuck = true;
		for (ScotlandYardPlayer player : this.players) {
			if (player.isDetective()) {
				if (player.hasTickets(BUS) || player.hasTickets(TAXI) || player.hasTickets(UNDERGROUND)) {
					allDetectivesStuck = false;
					break;
				}
			}
		}
		if (allDetectivesStuck) {
			this.winners.add(this.players.get(0).colour());
			return true;
		}

		// NO WIN CONDITION MET
		return false;
	}

	@Override
	public Colour getCurrentPlayer() {
		return this.players.get(this.currentPlayer).colour();
	}

	@Override
	public int getCurrentRound() {
		return this.currentRound;
	}

	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(this.rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		return new ImmutableGraph<Integer, Transport>(this.graph);
	}

}
