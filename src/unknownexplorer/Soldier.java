package unknownexplorer;

import java.util.List;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import repast.simphony.query.space.grid.GridCell;
import repast.simphony.query.space.grid.GridCellNgh;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import sajas.core.Agent;
import sajas.core.behaviours.Behaviour;
import sajas.core.behaviours.CyclicBehaviour;

public class Soldier extends Agent {
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;

	public AID myCaptain;

	private double xSoldier;
	private double ySoldier;
	private double velocitySoldier;
	private GridPoint search;
	private GridPoint goal;
	private double distanceToSearch;
	private int[] position = new int[2];

	public Soldier(ContinuousSpace<Object> space, Grid<Object> grid, AID myCaptain, int x, int y) {
		this.space = space;
		this.grid = grid;
		this.myCaptain = myCaptain;
		position[0] = x;
		position[1] = y;
		search = new GridPoint(position);

	}

	protected void setup() {
		xSoldier = position[0];
		ySoldier = position[1];
		velocitySoldier = 2;

		space.moveTo(this, xSoldier, ySoldier);
		grid.moveTo(this, (int) xSoldier, (int) ySoldier);

		addBehaviour(new SoldierBehaviour());
		addBehaviour(new MoveBehaviour());
		addBehaviour(new SearchGoal());

		System.out.println("Soldier " + getAID().getName() + " is ready.");
	}

	protected void takeDown() {
		System.out.println("Soldier " + getAID().getName() + " terminating.");
	}

	private class SoldierBehaviour extends Behaviour {
		private static final long serialVersionUID = 1L;
		private MessageTemplate mt;
		private int step = 0;

		public void action() {
			switch (step) {
			// Asks for information of where to start searching
			case 0:
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				cfp.addReceiver(myCaptain);
				cfp.setContent(xSoldier + "_" + ySoldier);
				cfp.setConversationId("comns");
				cfp.setReplyWith("cfp" + System.currentTimeMillis());
				myAgent.send(cfp);

				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("comns"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;

			// Receives information of where to start searching
			case 1:
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						String msg = reply.getContent();
						String[] parts = msg.split("_");
						int[] point = new int[2];
						point[0] = Integer.parseInt(parts[0]);
						point[1] = Integer.parseInt(parts[1]);
						distanceToSearch = Double.parseDouble(parts[2]);
						search = new GridPoint(point);
					} else if (reply.getPerformative() == ACLMessage.REFUSE) {
						// TODO: on hold, wait and ask again
					}

					step = 0;
				}
				break;

			// Confirms that received a position to search
			case 2:
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(myCaptain);
				order.setContent(search.getX() + "_" + search.getY());
				order.setConversationId("comns");
				order.setReplyWith("" + System.currentTimeMillis());
				myAgent.send(order);

				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("comns"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
			}
		}

		public boolean done() {
			if (step == 3) {
				return true;
			}
			return false;
		}
	}

	/**
	 * Moves the Soldier to reach the desired search position.
	 */
	private class MoveBehaviour extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			double distance = Math.abs(search.getX() - xSoldier) + Math.abs(search.getY() - ySoldier);
			if (distanceToSearch > 0 && distance < 0.2) {
				distanceToSearch--;
				int[] point = new int[2];
				point[0] = search.getX() + 1;
				point[1] = search.getY();
				search = new GridPoint(point);
			}
			try {
				moveTowards(search);
			} catch (NullPointerException e) {
			}
		}
	}

	/**
	 * Searches for the goal on the area next to the Soldier.
	 */
	private class SearchGoal extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			GridPoint pt = grid.getLocation(myAgent);

			GridCellNgh<Objective> nghCreator = new GridCellNgh<Objective>(grid, pt, Objective.class, 1, 1);
			List<GridCell<Objective>> gridCells = nghCreator.getNeighborhood(true);

			for (GridCell<Objective> cell : gridCells) {
				if (cell.size() > 0) {
					goal = cell.getPoint();
					search = goal;
					addBehaviour(new SendGoal());
				}
			}
		}
	}

	/**
	 * Sends the goal to the captain.
	 */
	private class SendGoal extends Behaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
			cfp.addReceiver(myCaptain);
			cfp.setContent(goal.getX() + "_" + goal.getY());
			cfp.setConversationId("goal");
			cfp.setReplyWith("cfp" + System.currentTimeMillis());
			myAgent.send(cfp);
		}

		@Override
		public boolean done() {
			// TODO Receive confirmation of received information
			return false;
		}
	}

	public void moveTowards(GridPoint pt) {
		if (!pt.equals(grid.getLocation(this))) {
			if (xSoldier > pt.getX()) {
				xSoldier -= velocitySoldier;
			} else {
				xSoldier += velocitySoldier;
			}

			if (ySoldier > pt.getY()) {
				ySoldier -= velocitySoldier;
			} else {
				ySoldier += velocitySoldier;
			}

			space.moveTo(this, xSoldier, ySoldier);
			grid.moveTo(this, (int) xSoldier, (int) ySoldier);
		}
	}
}
