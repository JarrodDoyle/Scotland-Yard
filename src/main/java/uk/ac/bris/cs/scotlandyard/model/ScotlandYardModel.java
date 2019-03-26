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

	private Set<Move> validMove(Colour colour) {
		HashSet<Move> moves = new HashSet<Move>();
		moves.add(new PassMove(colour));
		return moves;
	}

	@Override
	public void startRotate() {
		ScotlandYardPlayer currentPlayer = this.players.get(this.currentPlayer);
		this.moves = validMove(currentPlayer.colour());
		currentPlayer.player().makeMove(this, currentPlayer.location(), this.moves, this);
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
