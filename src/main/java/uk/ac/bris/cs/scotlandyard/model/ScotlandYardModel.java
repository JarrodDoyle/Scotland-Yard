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

// TODO implement all methods and pass all tests
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
		// testEmptyRoundsShouldThrow
		if (rounds.isEmpty()) {
			throw new IllegalArgumentException("Empty rounds");
		}
		// testNullRoundsShouldThrow
		this.rounds = requireNonNull(rounds);

		// testEmptyMapShouldThrow
		if (graph.isEmpty()) {
			throw new IllegalArgumentException("Empty graph/map");
		}
		// testNullMapShouldThrow
		this.graph = requireNonNull(graph);

		// testSwappedMrXShouldThrow
		// testNoMrXShouldThrow
		if (mrX.colour.isDetective()) {
			throw new IllegalArgumentException("MrX should be BLACK");
		}

		ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
		// testNullMrXShouldThrow
		configurations.add(requireNonNull(mrX));
		// testNullDetectiveShouldThrow
		configurations.add(requireNonNull(firstDetective));
		for (PlayerConfiguration config : restOfTheDetectives){
			// testAnyNullDetectiveShouldThrow
			configurations.add(requireNonNull(config));
		}

		Set<Integer> locations = new HashSet<>();
		Set<Colour> colours = new HashSet<>();
		for (PlayerConfiguration config : configurations) {
			// testLocationOverlapBetweenDetectivesShouldThrow
			// testLocationOverlapBetweenMrXAndDetectiveShouldThrow
			if (locations.contains(config.location)){
				throw new IllegalArgumentException("Duplicate player location");
			}
			locations.add(config.location);

			// testMoreThanOneMrXShouldThrow
			// testDuplicateDetectivesShouldThrow
			if (colours.contains(config.colour)){
				throw new IllegalArgumentException("Duplicate player colour");
			}
			colours.add(config.colour);

			// testDetectiveMissingAnyTicketsShouldThrow
			// testMrXMissingAnyTicketsShouldThrow
			if (config.tickets.get(BUS) == null) {
				throw new IllegalArgumentException("Player missing BUS ticket");
			}
			if (config.tickets.get(DOUBLE) == null) {
				throw new IllegalArgumentException("Player missing DOUBLE ticket");
			}
			if (config.tickets.get(SECRET) == null) {
				throw new IllegalArgumentException("Player missing SECRET ticket");
			}
			if (config.tickets.get(TAXI) == null) {
				throw new IllegalArgumentException("Player missing TAXI ticket");
			}
			if (config.tickets.get(UNDERGROUND) == null) {
				throw new IllegalArgumentException("Player missing UNDERGROUND ticket");
			}

			if (config.colour.isDetective()) {
				// testDetectiveHaveDoubleTicketShouldThrow
				if (config.tickets.get(DOUBLE) > 0){
					throw new IllegalArgumentException("Detective has DOUBLE");
				}
				// testDetectiveHaveSecretTicketShouldThrow
				if (config.tickets.get(SECRET) > 0){
					throw new IllegalArgumentException("Detective has SECRET");
				}
			}
		}

		// Put PlayerConfiguration into ScotlandYardPlayer so that it is mutable
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

	private Set<Move> doubleMoves(Colour colour, Integer location, Ticket prevTicket, Integer bus, Integer taxi, Integer underground, Integer secret) {
		HashSet<Move> moves = new HashSet<Move>();
		if (!colour.isDetective() && getPlayerTickets(colour, DOUBLE).get() > 0) {
			Collection<Edge<Integer,Transport>> edges = this.graph.getEdgesFrom(this.graph.getNode(location));
			for (Edge<Integer,Transport> edge : edges) {
				Transport transport = edge.data();
				Integer destination = edge.destination().value();
				if (!locationOccupiedByDetective(destination) && this.currentRound < this.getRounds().size() - 1) {
					if (transport == Transport.BUS && bus > 0) {
						moves.add(new DoubleMove(colour, prevTicket, location, BUS, destination));
					}
					else if (transport == Transport.TAXI && taxi > 0) {
						moves.add(new DoubleMove(colour, prevTicket, location, TAXI, destination));
					}
					else if (transport == Transport.UNDERGROUND && underground > 0) {
						moves.add(new DoubleMove(colour, prevTicket, location, UNDERGROUND, destination));
					}

					if (secret > 0) {
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
		Integer bus = getPlayerTickets(colour, BUS).get();
		Integer taxi = getPlayerTickets(colour, TAXI).get();
		Integer underground = getPlayerTickets(colour, UNDERGROUND).get();
		Integer secret = getPlayerTickets(colour, SECRET).get();
		for (Edge<Integer,Transport> edge : edges) {
			Transport transport = edge.data();
			Integer destination = edge.destination().value();
			if (!locationOccupiedByDetective(destination)) {
				if (transport == Transport.BUS && bus > 0) {
					moves.add(new TicketMove(colour, BUS, destination));
					moves.addAll(doubleMoves(colour, destination, BUS, bus - 1, taxi, underground, secret));
				}
				else if (transport == Transport.TAXI && taxi > 0) {
					moves.add(new TicketMove(colour, TAXI, destination));
					moves.addAll(doubleMoves(colour, destination, TAXI, bus, taxi - 1, underground, secret));
				}
				else if (transport == Transport.UNDERGROUND && underground > 0) {
					moves.add(new TicketMove(colour, UNDERGROUND, destination));
					moves.addAll(doubleMoves(colour, destination, UNDERGROUND, bus, taxi, underground - 1, secret));
				}
				if (secret > 0) {
					moves.add(new TicketMove(colour, SECRET, destination));
					moves.addAll(doubleMoves(colour, destination, SECRET, bus, taxi, underground, secret - 1));
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
		for (Spectator spectator : this.spectators) {
			spectator.onRoundStarted(this, this.currentRound);
		}
	}

	@Override
	public void startRotate() {
		if (isGameOver()) {
			throw new IllegalStateException("Game is over, cannot start new rotation.");
		}
		this.currentPlayer = 0;
		for (int i=0; i < this.players.size(); i++) {
			if (i == this.currentPlayer) {
				ScotlandYardPlayer player = this.players.get(i);
				this.moves = validMoves(player.colour());
				player.player().makeMove(this, player.location(), this.moves, this);
			}
		}
		if (isGameOver()){
			notifyOnGameOver();
		}
		else if (this.prevPlayer == this.players.size() - 1){
			notifyOnRotationComplete();
		}
	}

	@Override
	public void accept(Move m) {
		// testCallbackIsNotNull
		// testCallbackWithNullWillThrow
		requireNonNull(m);
		// testCallbackWithIllegalMoveNotInGivenMovesWillThrow
		if (!this.moves.contains(m)){
			throw new IllegalArgumentException(String.format("Move not in MOVES", m.toString()));
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
		// notification
		Colour player = this.players.get(this.prevPlayer).colour();
		TicketMove firstMove;
		if (this.rounds.get(this.currentRound)) {
			firstMove = move.firstMove();
		}
		else {
			firstMove = new TicketMove(player, move.firstMove().ticket(), this.prevMrXLocation);
		}
		TicketMove secondMove;
		if (this.rounds.get(this.currentRound + 1)) {
			secondMove = move.secondMove();
		}
		else {
			secondMove = new TicketMove(player, move.secondMove().ticket(), firstMove.destination());
		}
		this.players.get(0).removeTicket(DOUBLE);
		notifyOnMoveMade(new DoubleMove(player, firstMove, secondMove));

		move.firstMove().visit(this);
		move.secondMove().visit(this);
	}

	@Override
	public void visit(PassMove move) {
		if (this.players.get(this.prevPlayer).isMrX()) {
			if (this.currentRound != this.getRounds().size() && this.getRounds().get(this.currentRound)) {
				this.prevMrXLocation = this.players.get(0).location();
			}
			this.currentRound += 1;
			notifyOnRoundStarted();
		}
		notifyOnMoveMade(move);
	}

	@Override
	public void visit(TicketMove move) {
		ScotlandYardPlayer player = this.players.get(this.prevPlayer);
		player.removeTicket(move.ticket());
		player.location(move.destination());

		if (player.isDetective()) {
			this.players.get(0).addTicket(move.ticket());
			notifyOnMoveMade(move);
		}
		else {
			if (this.currentRound != this.getRounds().size() && this.getRounds().get(this.currentRound)) {
				this.prevMrXLocation = this.players.get(0).location();
			}
			this.currentRound += 1;
			notifyOnRoundStarted();
			notifyOnMoveMade(new TicketMove(player.colour(), move.ticket(), this.prevMrXLocation));
		}
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		if (this.spectators.contains(spectator)) {
			throw new IllegalArgumentException("Spectators already contains SPECTATOR");
		}
		// testRegisterNullSpectatorShouldThrow
		this.spectators.add(requireNonNull(spectator));
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		// testUnregisterNullSpectatorShouldThrow
		requireNonNull(spectator);
		if (!this.spectators.contains(spectator)) {
			throw new IllegalArgumentException("Spectators list does not contain SPECTATOR");
		}
		for (int i=0; i<this.spectators.size(); i++) {
			if (this.spectators.get(i).equals(spectator)) {
				this.spectators.remove(i);
			}
		}
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableList(this.spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		// testGetPlayersMatchesSupplied
		// testGetPlayersStartsWithBlack
		List<Colour> colours = new ArrayList<Colour>();
		for (ScotlandYardPlayer player : this.players){
			colours.add(player.colour());
		}
		// testGetPlayersIsImmutable
		return Collections.unmodifiableList(colours);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		return Collections.unmodifiableSet(this.winners);
	}

	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		// testGetPlayerLocationConcealsMrXLocationInitially
		if (!colour.isDetective()){
			return Optional.of(this.prevMrXLocation);
		}
		// testGetDetectiveLocationMatchesSupplied
		for (ScotlandYardPlayer player : this.players){
			if (player.colour() == colour){
				return Optional.of(player.location());
			}
		}
		// testGetPlayerLocationForNonExistentPlayerIsEmpty
		return Optional.empty();
	}

	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		// testGetPlayerTicketsMatchesSupplied
		for (ScotlandYardPlayer player : this.players){
			if (player.colour() == colour){
				return Optional.of(player.tickets().get(ticket));
			}
		}
		// testGetPlayerTicketsForNonExistentPlayerIsEmpty
		return Optional.empty();
	}

	@Override
	public boolean isGameOver() {
		// CASE: Mr.X is captured
		// WINNERS: Detectives
		if (locationOccupiedByDetective(this.players.get(0).location())){
			for (ScotlandYardPlayer player : this.players) {
				if (player.isDetective()) {
					this.winners.add(player.colour());
				}
			}
			return true;
		}

		// CASE: Mr.X is cornered
		// WINNERS: Detectives
		if (this.prevPlayer == this.players.size() - 1 && validMoves(this.players.get(0).colour()).size() == 1) {
			ScotlandYardPlayer mrX = this.players.get(0);
			if (mrX.hasTickets(BUS) || mrX.hasTickets(TAXI) || mrX.hasTickets(UNDERGROUND) || mrX.hasTickets(SECRET) || mrX.hasTickets(DOUBLE)) {
				for (ScotlandYardPlayer player : this.players) {
					if (player.isDetective()) {
						this.winners.add(player.colour());
					}
				}
				return true;
			}
		}

		// CASE: Mr.X cannot move
		// WINNERS: Detectives
		if (this.prevPlayer == this.players.size() - 1) {
			ScotlandYardPlayer mrX = this.players.get(0);
			if (!(mrX.hasTickets(BUS) || mrX.hasTickets(TAXI) || mrX.hasTickets(UNDERGROUND) || mrX.hasTickets(SECRET) || mrX.hasTickets(DOUBLE))) {
				for (ScotlandYardPlayer player : this.players) {
					if (player.isDetective()) {
						this.winners.add(player.colour());
					}
				}
				return true;
			}
		}


		// CASE: Mr.X is not captured in any round
		// WINNERS: Mr.X
		if (this.prevPlayer == this.players.size() - 1 && this.currentRound == this.rounds.size()) {
			this.winners.add(this.players.get(0).colour());
			return true;
		}

		// CASE: All detectives are stuck
		// WINNERS: Mr.X
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
		// testGetPlayerIsMrXInitially
		return this.players.get(this.currentPlayer).colour();
	}

	@Override
	public int getCurrentRound() {
		// testGetRoundIsNOT_STARTEDInitially
		return this.currentRound;
	}

	@Override
	public List<Boolean> getRounds() {
		// testGetRoundsIsImmutable
		// testGetRoundsMatchesSupplied
		return Collections.unmodifiableList(this.rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		// testGetGraphIsImmutable
		// testGetGraphMatchesSupplied
		return new ImmutableGraph<Integer, Transport>(this.graph);
	}

}
