package uk.ac.bris.cs.scotlandyard.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.*;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.Black;

import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;


public class ScotlandYardModel implements ScotlandYardGame {


	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;
	private ScotlandYardPlayer mrX, currentPlayer;
	private List<ScotlandYardPlayer> detectives;
	private int round;
	private int mrXLastLocation;
	private List<Spectator> spectators;

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
							 PlayerConfiguration mrX, PlayerConfiguration firstDetective,
							 PlayerConfiguration... restOfTheDetectives) {
		//adding players to store information
		this.mrX = new ScotlandYardPlayer(mrX.player, mrX.colour, mrX.location, mrX.tickets);
		this.detectives = new ArrayList<ScotlandYardPlayer>(1 + restOfTheDetectives.length);
		this.detectives.add(new ScotlandYardPlayer(firstDetective.player, firstDetective.colour, firstDetective.location, firstDetective.tickets));
		for (PlayerConfiguration detective : restOfTheDetectives) {
			this.detectives.add(new ScotlandYardPlayer(detective.player, detective.colour, detective.location, detective.tickets));
		}
		currentPlayer = this.mrX;
		round = 0;
		spectators = new ArrayList<>();

		this.rounds = requireNonNull(rounds);
		this.graph = requireNonNull(graph);

		mrX = requireNonNull(mrX);
		firstDetective = requireNonNull(firstDetective);
		for (PlayerConfiguration detective : restOfTheDetectives) {
			requireNonNull(detective);
		}

		if (rounds.isEmpty()) throw new IllegalArgumentException("Empty rounds");
		if (graph.isEmpty()) throw new IllegalArgumentException("Empty graph");

		// or mr.colour.isDetective()
		if (mrX.colour != Black) throw new IllegalArgumentException("MrX should be Black");

		//check players are not null
		ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
		for (PlayerConfiguration configuration : restOfTheDetectives) {
			configurations.add(requireNonNull(configuration));
		}
		configurations.add(0, firstDetective);
		configurations.add(0, mrX);

		//check players do not have duplicate locations
		Set<Integer> set = new HashSet<>();
		for (PlayerConfiguration configuration : configurations) {
			if (set.contains(configuration.location)) throw new IllegalArgumentException("Duplicate location");
			set.add(configuration.location);
		}

		//check players do not have duplicate colours
		Set<Colour> cSet = new HashSet<>();
		for (PlayerConfiguration configuration : configurations) {
			if (cSet.contains(configuration.colour)) throw new IllegalArgumentException("Duplicate colour");
			cSet.add(configuration.colour);
		}

		//checks if a player is missing a ticket
		if (isMissingTickets(mrX)) throw new IllegalArgumentException("Mr X missing tickets");
		if (isMissingTickets(firstDetective)) throw new IllegalArgumentException("Detective missing tickets");
		for (PlayerConfiguration detective : restOfTheDetectives) {
			if (isMissingTickets(detective)) throw new IllegalArgumentException("Detective missing tickets");
		}

		//check detectives do not have double or secret tickets
		if (hasDoubleTicket(firstDetective)){throw new IllegalArgumentException("Detective has Double Ticket");}
		for (PlayerConfiguration detective : restOfTheDetectives) {
			if (hasDoubleTicket(detective))
				throw new IllegalArgumentException("Detective has Double Ticket");
		}

		if (hasSecretTicket(firstDetective)){throw new IllegalArgumentException("Detective has Secret Ticket");}
		for (PlayerConfiguration detective : restOfTheDetectives) {
			if (hasSecretTicket(detective)) throw new IllegalArgumentException("Detective has Secret Ticket");
		}
	}

	//Checks if player is missing tickets
	private boolean isMissingTickets(PlayerConfiguration player) {
		for (Ticket t : Ticket.values()) {
			if (!player.tickets.containsKey(t)) return true;
		}
		return false;
	}

	//checks if the player has Double tickets
	private boolean hasDoubleTicket(PlayerConfiguration player) { return (player.tickets.get(Ticket.Double) != 0);}

	//checks if the player has Secret tickets
	private boolean hasSecretTicket(PlayerConfiguration player) { return (player.tickets.get(Ticket.Secret) != 0); }


	@Override
	public void registerSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if (spectators.contains(spectator)) throw new IllegalArgumentException("Spectator already in list");
		else spectators.add(spectator);
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if (spectators.isEmpty()) throw new IllegalArgumentException("No spectators in list");
		else spectators.remove(spectator);
	}

	private boolean noDetectivesIsAtDestination(int destination) {
		for (ScotlandYardPlayer detective: detectives)
			if (detective.location() == destination)
				return false;
		return true;
	}

	private boolean isLastRound() {return (getRounds().size() - getCurrentRound() < 2); }


	private Set<TicketMove> validTicketMoves(ScotlandYardPlayer player, int location) {
		Collection<Edge<Integer, Transport>> edges = graph.getEdgesFrom(graph.getNode(location));
		Set<TicketMove> tM = new HashSet<>();
		for (Edge<Integer, Transport> e : edges) {
			if (player.hasTickets(Ticket.fromTransport(e.data())) && noDetectivesIsAtDestination(e.destination().value())) {
				tM.add(new TicketMove(player.colour(), Ticket.fromTransport(e.data()), e.destination().value()));
			}
			if (player.hasTickets(Ticket.Secret) && noDetectivesIsAtDestination(e.destination().value())) {
				tM.add(new TicketMove(player.colour(), Ticket.Secret, e.destination().value()));
			}
		}
		return tM;
	}

	private Set<Move> validDoubleMoves(ScotlandYardPlayer player, Set<TicketMove> vsmoves) {
		Set<TicketMove> secondMoves;
		Set<Move> doubleMoves = new HashSet<>();
		for (TicketMove move : vsmoves) {
			secondMoves = validTicketMoves(player, move.destination());
			for (TicketMove move2 : secondMoves){
				if (player.hasTickets(move2.ticket())){
					if(move.ticket() == move2.ticket()){
						if(getPlayerTickets(player.colour(), move.ticket()) > 1){
							addTMoves(player, move, move2, doubleMoves);
						}
					}
					else{
						addTMoves(player, move, move2, doubleMoves);
					}
				}
			}
		}
		return doubleMoves;
	}


	private void addTMoves(ScotlandYardPlayer player, TicketMove t1, TicketMove t2, Set<Move> moves){
		moves.add(new DoubleMove(player.colour(), t1, t2));
		if (getPlayerTickets(player.colour(), Ticket.Secret) > 1) {
			moves.add(new DoubleMove(player.colour(), Ticket.Secret, t1.destination(), Ticket.Secret, t2.destination()));
		}
		if (getPlayerTickets(player.colour(), Ticket.Secret) == 1) {
			moves.add(new DoubleMove(player.colour(), Ticket.Secret, t1.destination(), t2.ticket(), t2.destination()));
			moves.add(new DoubleMove(player.colour(), t1.ticket(), t1.destination(), Ticket.Secret, t2.destination()));
		}
	}

	private Set<Move> validMoves(ScotlandYardPlayer player) {
		Set<Move> moves = new HashSet<>();
		Set<TicketMove> singleMoves = validTicketMoves(player, player.location());
		moves.addAll(singleMoves);

		if (player.hasTickets(Ticket.Double) && !isLastRound()) {
			Set<Move> doubleMoves = validDoubleMoves(player, singleMoves);
			moves.addAll(doubleMoves);
		}
		if (player.isDetective() && moves.isEmpty()) moves.add(new PassMove(player.colour()));

		return moves;
	}


	public void accept(Move move) {
		if (move == null) throw new NullPointerException("TicketMove can't be null");
		if (!validMoves(currentPlayer).contains(move)) throw new IllegalArgumentException("Invalid Move");
		else move.visit(new ScotlandYardVisitor(this));

	}

	class ScotlandYardVisitor implements MoveVisitor
	{
		ScotlandYardView view;

		public ScotlandYardVisitor(ScotlandYardModel model)
		{
			view = model;

		}
		public void visit(PassMove move){
			for (Spectator spectator : spectators) spectator.onMoveMade(view, move);
			if (detectives.indexOf(currentPlayer) == detectives.size()-1) {
				currentPlayer = mrX;
				for (Spectator spectator : spectators) spectator.onRotationComplete(view);
			}
			else {
				if (isGameOver()) for (Spectator spectator : spectators) spectator.onGameOver(view,getWinningPlayers());
				else {
					currentPlayer = detectives.get(detectives.indexOf(currentPlayer) + 1);
					currentPlayer.player().makeMove(view, currentPlayer.location(), validMoves(currentPlayer), ScotlandYardModel.this::accept);
				}
			}
		}


		public void visit(TicketMove move) {
			if (currentPlayer.isMrX()) {
				round++;
				currentPlayer.removeTicket(move.ticket());
				mrX.location(move.destination());
				if (isRevealRound()) mrXLastLocation = mrX.location();
				for (Spectator spectator : spectators) {
					spectator.onRoundStarted(view, round);
					if (isRevealRound()) spectator.onMoveMade(view, move);
					else spectator.onMoveMade(view, new TicketMove(mrX.colour(), move.ticket(), mrXLastLocation));
				}
				currentPlayer = detectives.get(0);
				currentPlayer.player().makeMove(view, currentPlayer.location(), validMoves(currentPlayer),
						ScotlandYardModel.this::accept);
			} else {
				currentPlayer.removeTicket(move.ticket());
				mrX.addTicket(move.ticket());
				currentPlayer.location(move.destination());
				for (Spectator spectator : spectators) spectator.onMoveMade(view, move);
				if (detectives.indexOf(currentPlayer) == (detectives.size() - 1)) {
					currentPlayer = mrX;
					if (isGameOver()) {
						for (Spectator spectator : spectators) spectator.onGameOver(view, getWinningPlayers());
					} else for (Spectator spectator : spectators) spectator.onRotationComplete(view);
				} else {
					if (mrXcaptured())
						for (Spectator spectator : spectators) spectator.onGameOver(view, getWinningPlayers());
					else {
						currentPlayer = detectives.get(detectives.indexOf(currentPlayer) + 1);
						currentPlayer.player().makeMove(view, currentPlayer.location(), validMoves(currentPlayer),
								ScotlandYardModel.this::accept);
					}
				}
			}

		}

		public void visit(DoubleMove move) {
			mrX.removeTicket(Ticket.Double);
			round++;
			if (isRevealRound()) {
				round++;
				if (isRevealRound()) {
					round = round - 2;
					for (Spectator spectator : spectators)
						spectator.onMoveMade(view, move);
				}
				else{
					round = round - 2;
					for (Spectator spectator : spectators)
						spectator.onMoveMade(view, new DoubleMove(mrX.colour(), move.firstMove(), new TicketMove(mrX.colour(), move.secondMove().ticket(), move.firstMove().destination())));
				}
			}
			else{
				round++;
				if (isRevealRound()) {
					round = round - 2;
					for (Spectator spectator : spectators)
						spectator.onMoveMade(view, new DoubleMove(mrX.colour(), new TicketMove(mrX.colour(), move.firstMove().ticket(), mrXLastLocation), move.secondMove()));
				}
				else {
					round = round - 2;
					for (Spectator spectator : spectators) spectator.onMoveMade(view, new DoubleMove(mrX.colour(), move.firstMove().ticket(), mrXLastLocation, move.secondMove().ticket(), mrXLastLocation));
				}
			}
			// firstMove
			round++;
			mrX.removeTicket(move.firstMove().ticket());
			mrX.location(move.firstMove().destination());
			if (isRevealRound()) mrXLastLocation = mrX.location();
			for (Spectator spectator : spectators) {
				spectator.onRoundStarted(view, round);
				if (isRevealRound()) spectator.onMoveMade(view, move.firstMove());
				else spectator.onMoveMade(view, new TicketMove(mrX.colour(), move.firstMove().ticket(), mrXLastLocation));
			}
			round++;
			// secondMove
			mrX.removeTicket(move.secondMove().ticket());
			mrX.location(move.secondMove().destination());
			if (isRevealRound()) mrXLastLocation = mrX.location();
			for (Spectator spectator : spectators) {
				spectator.onRoundStarted(view, round);
				if (isRevealRound()) spectator.onMoveMade(view, move.secondMove());
				else spectator.onMoveMade(view, new TicketMove(mrX.colour(), move.secondMove().ticket(),mrXLastLocation));
			}
			currentPlayer = detectives.get(0);
			currentPlayer.player().makeMove(view, currentPlayer.location(), validMoves(currentPlayer),
					ScotlandYardModel.this::accept);
		}
	}

	@Override
	public void startRotate() {
		if (isNull(this)) throw new NullPointerException();
		if (!getCurrentPlayer().isMrX()) throw new IllegalArgumentException("The rotation must start with Mr X!");
		if (isGameOver()) {
			for (Spectator spectator : spectators) spectator.onGameOver(this, getWinningPlayers());
			throw new IllegalStateException("Game is already over");
		}
		else if (!mrXcaptured()) currentPlayer.player().makeMove(this, currentPlayer.location(), validMoves(currentPlayer),
				this::accept);
	}

	@Override
	public Collection<Spectator> getSpectators() {return Collections.unmodifiableCollection(spectators);}

	@Override
	public List<Colour> getPlayers() {
		List<Colour> colours = new ArrayList<>(1 + detectives.size());
		colours.add(mrX.colour());
		for (ScotlandYardPlayer detective : detectives) {
			colours.add(detective.colour());
		}
		return Collections.unmodifiableList(colours);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		Set<Colour> winningPlayers = new HashSet<>();

		if (isGameOver()){
			if (mrXescaped() || detectivesHasNoTickets() || detectivesStuck()) winningPlayers.add(mrX.colour());
			else{
				for (ScotlandYardPlayer detective : detectives) {
					winningPlayers.add(detective.colour());
				}
			}
		}
		return Collections.unmodifiableSet(winningPlayers);
	}

	private ScotlandYardPlayer getPlayer(Colour colour) {
		if (colour.isMrX()) return mrX;
		for (ScotlandYardPlayer detective: detectives){
			if (detective.colour() == colour) return detective;
		}
		throw new IllegalArgumentException("Can't find player");
	}
	@Override
	public int getPlayerLocation(Colour colour) {
		if (colour.isMrX()) return mrXLastLocation;
		return getPlayer(colour).location();
	}

	@Override
	public int getPlayerTickets(Colour colour, Ticket ticket) {
		return getPlayer(colour).tickets().get(ticket);
	}


	public Boolean mrXcaptured() {
		for (ScotlandYardPlayer detective : detectives) {
			if (detective.location() == mrX.location()) return true;
		}
		return false;
	}

	public boolean mrXescaped() {
		if (getCurrentRound() == rounds.size()) return true;
		return false;
	}

	public boolean mrXstuck() {
		if (currentPlayer.isMrX() && validMoves(currentPlayer).isEmpty()) return true;
		return false;
	}

	public boolean detectivesStuck(){
		int count = 0;
		for (ScotlandYardPlayer detective : detectives)
			if (validMoves(detective).size() == 1 && validMoves(detective).contains(new PassMove(detective.colour())))
				count++;
		if (count == detectives.size()) return true;
		return false;
	}

	public boolean detectivesHasNoTickets(){
		for (ScotlandYardPlayer detective : detectives) {
			if (detective.hasTickets(Ticket.Taxi) || detective.hasTickets(Ticket.Bus) || detective.hasTickets(Ticket.Underground))
				return false;
		}
		return true;
	}

	@Override
	public boolean isGameOver() {
		return (mrXescaped() 			 ||
				mrXcaptured() 			 ||
				mrXstuck() 				 ||
				detectivesHasNoTickets() ||
				detectivesStuck());
	}

	@Override
	public Colour getCurrentPlayer() {
		return currentPlayer.colour();
	}

	@Override
	public int getCurrentRound() {
		return round;
	}

	@Override
	public boolean isRevealRound() {return rounds.get(round-1);}

	//immutable list of moves whose length-1 is the number of moves MrX can play in the game
	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {return new ImmutableGraph<>(graph);}

}