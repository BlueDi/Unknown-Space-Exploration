package unknownexplorer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import repast.simphony.query.space.grid.GridCell;
import repast.simphony.query.space.grid.GridCellNgh;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridDimensions;
import repast.simphony.space.grid.GridPoint;
import sajas.core.Agent;
import sajas.core.behaviours.Behaviour;

/**
 * Soldier Agent, the one who searches.
 */
public class Soldier extends Agent {
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;

	public AID myCaptain;
	public AID whoAskedForHelp;

	private double xSoldier;
	private double ySoldier;
	private double xTempSoldier;
	private double yTempSoldier;
	private double velocitySoldier;
	private boolean inPosition;
	private boolean foundGoal;
	private boolean foundWall;
	private boolean waiting;
	private boolean solved;
	private GridPoint search;
	private GridPoint goal;
	private Wall wall;
	private double distanceToSearch;
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
	public Soldier(ContinuousSpace<Object> space, Grid<Object> grid, AID myCaptain, double visionRadius) {
		this.space = space;
		this.grid = grid;
		this.myCaptain = myCaptain;
		this.visionRadius = visionRadius;
	}

	/**
	 * Initialize the Soldier.
	 */
	protected void setup() {
		xSoldier = 0;
		ySoldier = 0;
		velocitySoldier = 1;

		foundGoal = false;

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
					if (reply.getPerformative() == ACLMessage.PROPAGATE) {
						foundGoal = true;
						String msg = reply.getContent();
						String[] parts = msg.split("_");
						updatePosition(parts[0], parts[1]);
						addBehaviour(new MoveBehaviour());
						step = 2;
					} else if (reply.getPerformative() == ACLMessage.PROXY) {
						String msg = reply.getContent();
						String[] parts = msg.split("_");
						updatePosition(parts[0], parts[1]);
						Iterator<?> it = reply.getAllReplyTo();
						whoAskedForHelp = (AID) it.next();
						addBehaviour(new Helping());
						step = 2;
					} else if (reply.getPerformative() == ACLMessage.INFORM) {
						String msg = reply.getContent();
						String[] parts = msg.split("_");
						updatePosition(parts[0], parts[1]);
						distanceToSearch = Double.parseDouble(parts[2]);
						myInfo = new int[(int) distanceToSearch];
						Arrays.fill(myInfo, 2);
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
			return step == 2;
		}
	}

	/**
	 * Moves the Soldier to reach the desired search position.
	 */
	private class MoveBehaviour extends Behaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			if (!foundWall) {
				try {
					moveToExplore(search);
					if (inPosition && !foundWall) {
						distanceToSearch--;
						int[] point = new int[2];
						point[0] = search.getX() + 1;
						point[1] = search.getY();
						search = new GridPoint(point);
					}
				} catch (NullPointerException e) {
				}
			}
		}

		@Override
		public boolean done() {
			if (inPosition && foundGoal) {
				return true;
			} else if (inPosition && distanceToSearch == 0) {
				inPosition = false;
				addBehaviour(new SoldierBehaviour());
				return true;
			}
			return false;
		}
	}

	private class Helping extends Behaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			moveTowards(search);
		}

		@Override
		public boolean done() {
			if (search.equals(grid.getLocation(myAgent))) {
				destroyWall(search);
				foundWall = false;
				ACLMessage message = new ACLMessage(ACLMessage.AGREE);
				message.setConversationId("solved");
				message.addReceiver(whoAskedForHelp);
				myAgent.send(message);
				addBehaviour(new SoldierBehaviour());
				return true;
			}
			return false;
		}

	}

	private class HelpListener extends Behaviour {
		private static final long serialVersionUID = 1L;
		private MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchConversationId("solved"),
				MessageTemplate.MatchPerformative(ACLMessage.AGREE));

		@Override
		public void action() {
			ACLMessage message = myAgent.receive(mt);

			if (message != null) {
				solved = true;
				System.out.println("Agent " + myAgent.getLocalName() + " got helped.");
			}
		}

		@Override
		public boolean done() {
			return solved;
		}
	}

	/**
	 * Searches for the goal on the area next to the Soldier.
	 */
	private class SearchGoal extends Behaviour {
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

		@Override
		public boolean done() {
			return foundGoal;
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
			return true;
		}
	}

	/**
	 * Updates the position of the Soldier.
	 * 
	 * @param x
	 * @param y
	 */
	private void updatePosition(String x, String y) {
		int[] point = new int[2];
		point[0] = (int) Double.parseDouble(x);
		point[1] = (int) Double.parseDouble(y);
		search = new GridPoint(point);
		xTempSoldier = point[0];
		yTempSoldier = point[1];
	}

	/**
	 * Checks if the area next to the Soldier has Walls.
	 * 
	 * @param pt
	 */
	private void analyseArea(GridPoint pt) {
		GridCellNgh<Wall> nghCreator = new GridCellNgh<Wall>(grid, pt, Wall.class, (int) visionRadius, 0);
		List<GridCell<Wall>> gridCells = nghCreator.getNeighborhood(true);

		int wallPos = -1;
		for (GridCell<Wall> cell : gridCells) {
			if ((cell.getPoint().getX() < (int) xSoldier + distanceToSearch) && cell.getPoint().getX() >= (int) xSoldier
					&& cell.getPoint().getY() == ySoldier) {
				wallPos = cell.getPoint().getX() - (int) xSoldier;
				myInfo[wallPos] = 3;
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
	 * Checks if there is a Wall at pt. Updates wall value.
	 * 
	 * @param pt
	 * @return true if there is
	 */
	private boolean isWall(GridPoint pt) {
		Iterable<Object> objects = grid.getObjectsAt(pt.getX(), pt.getY());
		for (Iterator<Object> iter = objects.iterator(); iter.hasNext();) {
			Object element = iter.next();
			if (element.getClass().equals(Wall.class)) {
				wall = (Wall) element;
				return true;
			}
		}
		return false;
	}

	private void checkFastDestroy(GridPoint pt) {
		GridCellNgh<Soldier> nghCreator = new GridCellNgh<Soldier>(grid, pt, Soldier.class, 1, 1);
		List<GridCell<Soldier>> gridCells = nghCreator.getNeighborhood(true);

		for (GridCell<Soldier> cell : gridCells) {
			Iterator<Soldier> it = cell.items().iterator();
			if (it.hasNext()) {
				if (it.next().getAID() != this.getAID()) {
					destroyWall(pt);
					foundWall = false;
					break;
				}
			}
		}
	}

	private void destroyWall(GridPoint pt) {
		GridCellNgh<Wall> nghCreator = new GridCellNgh<Wall>(grid, pt, Wall.class, 0, 0);
		List<GridCell<Wall>> gridCells = nghCreator.getNeighborhood(true);

		for (GridCell<Wall> cell : gridCells) {
			Iterator<Wall> it = cell.items().iterator();
			if (it.hasNext()) {
				Wall w = it.next();
				GridDimensions d = grid.getDimensions();
				grid.moveTo(w, d.getWidth() - 1, d.getHeight() - 1);
				space.moveTo(w, d.getWidth() - 1, d.getHeight() - 1);
			}
		}
	}

	private void askForHelp() {
		ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
		message.setConversationId("help");
		message.addReceiver(myCaptain);
		message.setContent(wall.getX() + "_" + wall.getY());
		this.send(message);
		addBehaviour(new HelpListener());
	}

	private void moveToExplore(GridPoint pt) {
		foundWall = isWall(pt);
		if (foundWall) {
			checkFastDestroy(pt);
		}
		moveTowards(pt);
	}

	/**
	 * Updates the position of the Soldier to move it towards pt.
	 * 
	 * @param pt
	 */
	public void moveTowards(GridPoint pt) {
		if (!foundWall && !waiting && !pt.equals(grid.getLocation(this))) {
			waiting = false;

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
		} else if (foundWall && !solved && !waiting) {
			waiting = true;
			inPosition = false;
			askForHelp();
		} else if (solved) {
			solved = false;
			foundWall = false;
			waiting = false;
			destroyWall(pt);
		} else {
			inPosition = true;
			analyseArea(pt);
		}
	}
}
