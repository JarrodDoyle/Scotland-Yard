package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.DOUBLE;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.SECRET;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.TAXI;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.BUS;
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
public class ScotlandYardModel implements ScotlandYardGame {
	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {

//Creates an arraylist of all the detectives
			ArrayList<PlayerConfiguration> detectives = new ArrayList<>();
			detectives.add(firstDetective);
			for (PlayerConfiguration detective : restOfTheDetectives) {
				detectives.add(detective);
			}

// Check rounds are not empty and not null
			if (rounds.isEmpty()) {
				throw new IllegalArgumentException("Rounds are empty");
			}
			this.rounds = requireNonNull(rounds);

//Check graph is not empty and not null
			if (graph.isEmpty()) {
				throw new IllegalArgumentException("Graph is empty");
			}
			this.graph = requireNonNull(graph);

//Check Mr X is black and not null
			if (requireNonNull(mrX).colour.isDetective()) {
				throw new IllegalArgumentException("MrX should be BLACK");
			}

//Check the detectives are not black and not null
			for (PlayerConfiguration detective : detectives) {
				if (requireNonNull(detective).colour.isMrX()) {
					throw new IllegalArgumentException("Detective cannot be BLACK");
				}
			}

//Check each detective's colour for collisions and
//check each player's data for collisions (location overlap)
		  ArrayList<Integer> locations = new ArrayList<Integer>();
			ArrayList<Colour> colours = new ArrayList<>();

			locations.add(mrX.location);
			for (PlayerConfiguration detective : detectives) {
				if (locations.contains(detective.location)) {
						throw new IllegalArgumentException("Duplicate Player locations");
				}
				else {
						locations.add(detective.location);
				}

				if (colours.contains(detective.colour)) {
					throw new IllegalArgumentException("Duplicate Detective colours");
				}
				else {
					colours.add(detective.colour);
				}
			}


//Check that all detectives do not have secret tickets or double tickets, and they have tickets for taxi bus and underground.
			for (PlayerConfiguration detective : detectives) {
				if (detective.tickets.get(SECRET) > 0) {
					throw new IllegalArgumentException("Detectives cannot have secret tickets");
				}
				if (detective.tickets.get(DOUBLE) > 0) {
					throw new IllegalArgumentException("Detectives cannot have double tickets");
				}
				if ((detective.tickets.get(TAXI) == 0) || (detective.tickets.get(BUS) == 0) || (detective.tickets.get(UNDERGROUND) == 0)) {
					throw new IllegalArgumentException("Detectives must have tickets for taxi, bus and underground");
				}
			}

//Check that MrX has all types of ticket
			if ((mrX.tickets.get(SECRET) == 0) || (mrX.tickets.get(DOUBLE) == 0) || (mrX.tickets.get(BUS) == 0) || (mrX.tickets.get(TAXI) == 0) || (mrX.tickets.get(UNDERGROUND) == 0)) {
				throw new IllegalArgumentException("MrX must have all types of ticket");
			}

		// TODO from testGetRoundsMatchesSupplied
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
	public void startRotate() {
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
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public boolean isGameOver() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Colour getCurrentPlayer() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public int getCurrentRound() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public List<Boolean> getRounds() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		// TODO
		throw new RuntimeException("Implement me");
	}

}
