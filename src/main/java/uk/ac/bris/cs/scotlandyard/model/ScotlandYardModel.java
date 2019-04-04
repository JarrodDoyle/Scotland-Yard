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
public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move> {
	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;
	private List<ScotlandYardPlayer> players;
	private Integer currentPlayer;
	private Integer currentRound;
	private Integer prevMrXLocation;
	private Set<Move> moves;

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
		this.prevMrXLocation = 0;
		this.currentRound = 0;
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
		Integer location = this.players.get(this.currentPlayer).location();
		Collection<Edge<Integer,Transport>> edges = this.graph.getEdgesFrom(this.graph.getNode(location));
		Integer bus = getPlayerTickets(colour, BUS).get();
		Integer taxi = getPlayerTickets(colour, TAXI).get();
		Integer underground = getPlayerTickets(colour, UNDERGROUND).get();
		Integer secret = getPlayerTickets(colour, SECRET).get();
		for (Edge<Integer,Transport> edge : edges) {
			Transport transport = edge.data();
			Integer destination = edge.destination().value();
			if (!locationOccupiedByDetective(destination) && this.currentRound < this.getRounds().size()) {
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

	@Override
	public void startRotate() {
		this.currentPlayer = 0;
		for (ScotlandYardPlayer x : this.players) {
			Colour player = x.colour();
			this.moves = validMoves(player);
			x.player().makeMove(this, x.location(), this.moves, this);
			this.currentPlayer += 1;
		}
	}

	@Override
	public void accept(Move m){
		// testCallbackIsNotNull
		// testCallbackWithNullWillThrow
		requireNonNull(m);
		// testCallbackWithIllegalMoveNotInGivenMovesWillThrow
		if (!this.moves.contains(m)){
			throw new IllegalArgumentException("Move not in MOVES");
		}
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Collection<Spectator> getSpectators() {
		// TODO
		throw new RuntimeException("Implement me");
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
		// TODO implement this properly
		return Collections.unmodifiableSet(new HashSet<Colour>());
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
		// TODO implement this properly
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
