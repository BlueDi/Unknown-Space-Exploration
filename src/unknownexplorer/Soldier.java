package unknownexplorer;

import java.util.Arrays;
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

/**
 * Soldier Agent, the one who searches.
 */
public class Soldier extends Agent {
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;

	public AID myCaptain;

	private double xSoldier;
	private double ySoldier;
	private double xTempSoldier;
	private double yTempSoldier;
	private double velocitySoldier;
	private boolean inPosition;
	private GridPoint search;
	private GridPoint goal;
	private double distanceToSearch;
	private int[] position = new int[2];
	private int[] myInfo;
	private double visionRadius;

	/**
	 * Soldier constructor.
	 * 
	 * @param space
	 * @param grid
	 * @param myCaptain
	 * @param x
	 * @param y
	 */
	public Soldier(ContinuousSpace<Object> space, Grid<Object> grid, AID myCaptain, int x, int y, double visionRadius) {
		this.space = space;
		this.grid = grid;
		this.myCaptain = myCaptain;
		position[0] = x;
		position[1] = y;
		this.visionRadius = visionRadius;
	}

	/**
	 * Initialize the Soldier.
	 */
	protected void setup() {
		xSoldier = position[0];
		ySoldier = position[1];
		velocitySoldier = 1;

		space.moveTo(this, xSoldier, ySoldier);
		grid.moveTo(this, (int) xSoldier, (int) ySoldier);

		addBehaviour(new SoldierBehaviour());
		addBehaviour(new SearchGoal());

		System.out.println("Soldier " + getAID().getName() + " is ready.");
	}

	/**
	 * Terminate the Soldier.
	 */
	protected void takeDown() {
		System.out.println("Soldier " + getAID().getName() + " terminating.");
	}

	/**
	 * The Soldier will ask his Captain for a position for him to search. With
	 * that position he will search within a radius. After he searches every
	 * position he has to, he will communicate with the Captain, which will give
	 * him another zone.
	 */
	private class SoldierBehaviour extends Behaviour {
		private static final long serialVersionUID = 1L;
		private MessageTemplate mt;
		private int step = 0;

		public void action() {
			switch (step) {
			// Asks for information of where to start searching
			case 0:
				mt = MessageTemplate.MatchConversationId("position_to_search");
				ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
				message.addReceiver(myCaptain);
				String content = generateSoldierReport();
				message.setContent(xTempSoldier + "_" + yTempSoldier + "_" + content);
				message.setConversationId("position_to_search");
				myAgent.send(message);

				step = 1;
				break;

			// Receives information of where to start searching
			case 1:
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					if (reply.getPerformative() == ACLMessage.INFORM) {
						String msg = reply.getContent();
						String[] parts = msg.split("_");
						int[] point = new int[2];
						point[0] = (int) Double.parseDouble(parts[0]);
						point[1] = (int) Double.parseDouble(parts[1]);
						xTempSoldier = point[0];
						yTempSoldier = point[1];
						distanceToSearch = Double.parseDouble(parts[2]);
						myInfo = new int[(int) distanceToSearch];
						Arrays.fill(myInfo, 2);
						search = new GridPoint(point);
						addBehaviour(new MoveBehaviour());
						step = 2;
					} else if (reply.getPerformative() == ACLMessage.REFUSE) {
						step = 0;
					}
				}
				break;
			}
		}

		public boolean done() {
			if (step == 2) {
				return true;
			}
			return false;
		}
	}

	/**
	 * Moves the Soldier to reach the desired search position.
	 */
	private class MoveBehaviour extends Behaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			try {
				moveTowards(search);
				if (inPosition) {
					distanceToSearch--;
					int[] point = new int[2];
					point[0] = search.getX() + 1;
					point[1] = search.getY();
					search = new GridPoint(point);
				}
			} catch (NullPointerException e) {
			}
		}

		@Override
		public boolean done() {
			if (distanceToSearch == 0) {
				inPosition = false;
				addBehaviour(new SoldierBehaviour());
				return true;
			}
			return false;
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
	 * Sends the goal to the Captain.
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

	/**
	 * Checks if the area next to the Soldier has Walls.
	 * 
	 * @param pt
	 */
	private void analyseArea(GridPoint pt) {
		GridCellNgh<Wall> nghCreator = new GridCellNgh<Wall>(grid, pt, Wall.class, (int) visionRadius, 0);
		List<GridCell<Wall>> gridCells = nghCreator.getNeighborhood(true);

		for (GridCell<Wall> cell : gridCells) {
			if ((cell.getPoint().getX() < (int) xSoldier + distanceToSearch)
					&& cell.getPoint().getX() >= (int) xSoldier) {
				myInfo[cell.getPoint().getX() - (int) xSoldier] = 3;
			}

		}
	}

	/**
	 * Creates a string with values found near the Soldier.
	 * 
	 * @return Values next to the Soldier
	 */
	private String generateSoldierReport() {
		String report = "";
		if (myInfo == null)
			return report;

		if (myInfo.length > 0) {
			for (int value : myInfo) {
				report += value + "_";
			}
			report = report.substring(0, report.length() - 1);
		}
		return report;
	}

	/**
	 * Updates the position of the Soldier to move it towards pt.
	 * 
	 * @param pt
	 */
	public void moveTowards(GridPoint pt) {
		if (!pt.equals(grid.getLocation(this))) {
			if (xSoldier > pt.getX()) {
				xSoldier -= velocitySoldier;
			} else if (xSoldier < pt.getX()) {
				xSoldier += velocitySoldier;
			}

			if (ySoldier > pt.getY()) {
				ySoldier -= velocitySoldier;
			} else if (ySoldier < pt.getY()) {
				ySoldier += velocitySoldier;
			}

			space.moveTo(this, xSoldier, ySoldier);
			grid.moveTo(this, (int) xSoldier, (int) ySoldier);
		} else {
			inPosition = true;
			analyseArea(pt);
		}
	}
}
