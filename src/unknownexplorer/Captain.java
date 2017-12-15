package unknownexplorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jade.core.AID;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import sajas.core.Agent;
import sajas.core.behaviours.Behaviour;
import sajas.core.behaviours.CyclicBehaviour;
import sajas.domain.DFService;

/**
 * Captain Agent, the one who commands.
 */
public class Captain extends Agent {
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;

	private List<jade.core.AID> otherCaptains;
	private List<AID> whoNeedHelp;
	private List<GridPoint> pointsThatNeedHelp;

	private AID general;
	private boolean ready;
	private boolean foundGoal;
	private double xCaptain;
	private double yCaptain;
	private double xTempCaptain;
	private double yTempCaptain;
	private GridPoint goal;
	private int communicationRadius; // TODO: communicationRadius must come from
	// the parameters.

	/**
	 * searchMatrix values will be 0, 1, 2, 3, or 4.
	 * <P>
	 * 0: default, unknown of what's in there;
	 * <P>
	 * 1: a soldier is searching it;
	 * <P>
	 * 2: position searched & empty;
	 * <P>
	 * 3: position searched & wall;
	 * <P>
	 * 4: another captain zone;
	 * <P>
	 * 5: goal.
	 */
	int[][] searchMatrix;

	public void setGeneral(AID general) {
		this.general = general;
	}

	public void setOtherCaptains(List<AID> otherCaptains) {
		this.otherCaptains = otherCaptains;
	}

	/**
	 * Captain constructor
	 * 
	 * @param space
	 * @param grid
	 * @param BOARD_DIM
	 */
	public Captain(ContinuousSpace<Object> space, Grid<Object> grid, int BOARD_DIM, int COMMUNICATION_RADIUS) {
		this.space = space;
		this.grid = grid;
		this.ready = false;
		searchMatrix = new int[BOARD_DIM][BOARD_DIM];
		this.communicationRadius = COMMUNICATION_RADIUS;
		whoNeedHelp = new ArrayList<AID>();
		pointsThatNeedHelp = new ArrayList<GridPoint>();
	}

	/**
	 * Initialize the Captain.
	 */
	protected void setup() {
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Comunication");
		sd.setName("JAJaS-Captain");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}

		addBehaviour(new ExchangeInformation());
		if (general != null)
			addBehaviour(new MoveToAnotherZone());
		addBehaviour(new ListenBroadcastGoal());
		addBehaviour(new ReceiveGoal());
		addBehaviour(new MoveBehaviour());
		System.out.println("Captain " + getAID().getName() + " is ready.");
	}

	/**
	 * Terminate the Captain.
	 */
	protected void takeDown() {
		System.out.println("Captain " + getAID().getName() + " terminating.");
	}

	/**
	 * Negotiation with the other agents about positioning.
	 */
	private class ExchangeInformation extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		public void action() {
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchConversationId("help"),
					MessageTemplate.or(MessageTemplate.MatchConversationId("position_to_search"),
							MessageTemplate.MatchConversationId("new_occupied_zone")));
			ACLMessage message = myAgent.receive(mt);
			if (message != null) {
				ACLMessage reply = message.createReply();
				if (message.getSender().getName().startsWith("Soldier")
						&& message.getConversationId() == "position_to_search"
						&& message.getPerformative() == ACLMessage.REQUEST) {
					if (!message.getContent().isEmpty())
						storeReport(message.getContent());

					if (foundGoal) {
						reply.setPerformative(ACLMessage.PROPAGATE);
						reply.setContent(goal.getX() + "_" + goal.getY());
					} else if (ready && !whoNeedHelp.isEmpty() && !pointsThatNeedHelp.isEmpty()
							&& whoNeedHelp.get(0) != message.getSender()) {
						reply.setPerformative(ACLMessage.PROXY);
						GridPoint gp = pointsThatNeedHelp.remove(0);
						reply.setContent(gp.getX() + "_" + gp.getY());
						reply.addReplyTo(whoNeedHelp.remove(0));
					} else if (ready && checkNearFreePositions()) {
						reply.setPerformative(ACLMessage.INFORM);
						reply.setContent(getSearchInfo());
					} else if (ready && !checkSoldierWorking()) {
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("");
						addBehaviour(new MoveToAnotherZone());
					} else {
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("");
					}
				} else if (message.getSender().getName().startsWith("Soldier") && message.getConversationId() == "help"
						&& message.getPerformative() == ACLMessage.REQUEST) {
					reply.setPerformative(ACLMessage.UNKNOWN);
					String msg = message.getContent();
					String[] parts = msg.split("_");
					int[] point = new int[2];
					point[0] = (int) Double.parseDouble(parts[0]);
					point[1] = (int) Double.parseDouble(parts[1]);
					pointsThatNeedHelp.add(new GridPoint(point));
					whoNeedHelp.add(message.getSender());
				} else if (general == null && message.getSender().getName().startsWith("Captain")
						&& message.getConversationId() == "new_occupied_zone"
						&& message.getPerformative() == ACLMessage.PROPOSE) {
					String msg = message.getContent();
					String[] parts = msg.split("_");
					int[] point = new int[3];
					point[0] = Integer.parseInt(parts[0]);
					point[1] = Integer.parseInt(parts[1]);
					point[2] = Integer.parseInt(parts[2]);
					if (point[0] >= searchMatrix.length || point[1] >= searchMatrix.length) {
						reply.setPerformative(ACLMessage.CANCEL);
					} else if (searchMatrix[point[1]][point[0]] == 0) {
						reply.setPerformative(ACLMessage.CONFIRM);
						updateSearchMatrixCaptain(point[0], point[1], point[2]);
					} else {
						reply.setPerformative(ACLMessage.CANCEL);
					}
					reply.setContent(msg);
				} else if (message.getSender().getName().startsWith("Captain")
						&& message.getConversationId() == "new_occupied_zone"
						&& message.getPerformative() == ACLMessage.CONFIRM) {
					ready = true;
					String msg = message.getContent();
					String[] parts = msg.split("_");
					int[] point = new int[2];
					point[0] = Integer.parseInt(parts[0]);
					point[1] = Integer.parseInt(parts[1]);
					xTempCaptain = point[0];
					yTempCaptain = point[1];
					int distance = Integer.parseInt(parts[2]);
					goal = new GridPoint(point);
					updateSearchMatrixCaptain(point[0], point[1], distance);
					reply.setPerformative(ACLMessage.UNKNOWN);
				} else if (message.getSender().getName().startsWith("Captain")
						&& message.getConversationId() == "new_occupied_zone"
						&& message.getPerformative() == ACLMessage.CANCEL) {
					reply.setPerformative(ACLMessage.UNKNOWN);
					String msg = message.getContent();
					String[] parts = msg.split("_");
					int[] point = new int[3];
					point[0] = Integer.parseInt(parts[0]);
					point[1] = Integer.parseInt(parts[1]);
					point[2] = Integer.parseInt(parts[2]);
					updateSearchMatrixCaptain(point[0], point[1], point[2]);
					addBehaviour(new MoveToAnotherZone());
				}

				myAgent.send(reply);
			}
		}
	}

	/**
	 * Behaviour to change the position of the captain to another zone and
	 * communicate his new zone to the other captains.
	 */
	private class MoveToAnotherZone extends Behaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			ACLMessage newZoneMessage = new ACLMessage(ACLMessage.PROPOSE);
			newZoneMessage.setConversationId("new_occupied_zone");
			ready = false;

			boolean found = false;
			int i;
			int j;
			int xFirst0 = -1;
			int yFirst0 = -1;
			int nPositions = 2 * communicationRadius;
			int xCounter = 0;
			int yCounter = 0;

			for (j = 0; j < searchMatrix.length; j++) {
				for (i = 0; i < searchMatrix.length; i++) {
					if (searchMatrix[j][i] == 0) {
						found = true;
						xFirst0 = i;
						yFirst0 = j;
						break;
					}
				}
				if (found) {
					break;
				}
			}

			for (j = yFirst0; j < searchMatrix.length && yCounter < nPositions; j++, yCounter++) {
				xCounter = 0;
				for (i = xFirst0; i < searchMatrix.length && xCounter < nPositions; i++) {
					if (searchMatrix[j][i] == 0) {
						xCounter++;
					} else {
						break;
					}
				}

				if (xCounter < nPositions) {
					nPositions = xCounter;
				}
			}

			nPositions = (xCounter >= yCounter) ? yCounter : xCounter;
			if (nPositions == 1) {
				nPositions = 2;
			}
			newZoneMessage
					.setContent((xFirst0 + nPositions / 2) + "_" + (yFirst0 + nPositions / 2) + "_" + (nPositions / 2));

			if (general != null) {
				newZoneMessage.addReceiver(general);
				myAgent.send(newZoneMessage);
			}
		}

		@Override
		public boolean done() {
			return true;
		}
	}

	/**
	 * When the captain receives information about the goal from one of his
	 * soldiers he will broadcast the goal position to the other captains.
	 */
	private class BroadcastGoal extends Behaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
			for (int k = 0; k < otherCaptains.size(); k++) {
				cfp.addReceiver(otherCaptains.get(k));
			}
			cfp.setContent(goal.getX() + "_" + goal.getY());
			cfp.setConversationId("broadcast_goal");
			cfp.setReplyWith("cfp" + System.currentTimeMillis());
			myAgent.send(cfp);
		}

		@Override
		public boolean done() {
			return false;
		}
	}

	/**
	 * The Captain is always listening to other captains information about the
	 * goal.
	 */
	private class ListenBroadcastGoal extends Behaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchConversationId("broadcast_goal");
			ACLMessage reply = myAgent.receive(mt);

			if (reply != null) {
				foundGoal = true;
				String msg = reply.getContent();
				String[] parts = msg.split("_");
				int[] point = new int[2];
				point[0] = Integer.parseInt(parts[0]);
				point[1] = Integer.parseInt(parts[1]);
				goal = new GridPoint(point);
			}
		}

		@Override
		public boolean done() {
			return foundGoal;
		}
	}

	/**
	 * The captain will always be trying to reach the goal.
	 * <P>
	 * The goal can be the final destination or the new search position of the
	 * captain.
	 */
	private class MoveBehaviour extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			try {
				moveTowards(goal);
			} catch (NullPointerException e) {
			}
		}
	}

	/**
	 * Behaviour to receive information about the goal from a Soldier.
	 */
	private class ReceiveGoal extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		public void action() {
			MessageTemplate mt = MessageTemplate.MatchConversationId("goal");
			ACLMessage message = myAgent.receive(mt);
			if (message != null) {
				foundGoal = true;
				String msg = message.getContent();

				String[] parts = msg.split("_");
				int[] point = new int[2];
				point[0] = Integer.parseInt(parts[0]);
				point[1] = Integer.parseInt(parts[1]);
				goal = new GridPoint(point);
				addBehaviour(new BroadcastGoal());
			} else {
				block();
			}
		}
	}

	/**
	 * Checks if there are free positions on the captain area.
	 * 
	 * @return True if there are
	 */
	private boolean checkNearFreePositions() {
		return checkZoneHas(1);
	}

	/**
	 * Checks if there are Soldiers working on the Captain area.
	 * 
	 * @return True if there are
	 */
	private boolean checkSoldierWorking() {
		return checkZoneHas(4);
	}

	/**
	 * Checks there is values of toSearch in the Captain zone.
	 * 
	 * @return True if there are
	 */
	private boolean checkZoneHas(int toSearch) {
		int xInit = (int) (xTempCaptain - communicationRadius);
		int xLast = (int) (xTempCaptain + communicationRadius);
		int yInit = (int) (yTempCaptain - communicationRadius);
		int yLast = (int) (yTempCaptain + communicationRadius);

		xInit = xInit < 0 ? 0 : xInit;
		xLast = xLast > searchMatrix.length ? searchMatrix.length : xLast;
		yInit = yInit < 0 ? 0 : yInit;
		yLast = yLast > searchMatrix.length ? searchMatrix.length : yLast;

		for (int j = yInit; j < yLast; j++) {
			for (int i = xInit; i < xLast; i++) {
				if (searchMatrix[j][i] == toSearch) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Calculates where the Soldier will start searching.
	 * 
	 * @return String with position where to start and the distance
	 */
	private String getSearchInfo() {
		boolean found = false;
		int xSoldier = -1;
		int ySoldier = -1;
		int counter = 0;
		int xInit = (int) (xTempCaptain - communicationRadius);
		int xLast = (int) (xTempCaptain + communicationRadius);
		int yInit = (int) (yTempCaptain - communicationRadius);
		int yLast = (int) (yTempCaptain + communicationRadius);

		xInit = xInit < 0 ? 0 : xInit;
		xLast = xLast > searchMatrix.length ? searchMatrix.length : xLast;
		yInit = yInit < 0 ? 0 : yInit;
		yLast = yLast > searchMatrix.length ? searchMatrix.length : yLast;

		for (int i = xInit; i < xLast; i++) {
			for (int j = yInit; j < yLast; j++) {
				if (searchMatrix[j][i] == 1) {
					found = true;
					xSoldier = i;
					ySoldier = j;
					break;
				}
			}
			if (found) {
				break;
			}
		}

		if (found) {
			int nPositions = 2 * communicationRadius;
			for (int k = xSoldier; k < xLast && counter < nPositions; k++) {
				if (searchMatrix[ySoldier][k] == 1) {
					counter++;
				} else {
					break;
				}
			}

			updateSearchMatrixSoldier(xSoldier, ySoldier, counter);
		}

		return xSoldier + "_" + ySoldier + "_" + counter;
	}

	/**
	 * Updates the searchMatrix.
	 * 
	 * @param column
	 * @param row
	 * @param distance
	 */
	private void updateSearchMatrixSoldier(int column, int row, int distance) {
		for (int i = 0; i < distance; i++) {
			searchMatrix[row][column + i] = 4;
		}
	}

	/**
	 * Updates the searchMatrix.
	 * 
	 * @param column
	 *            X
	 * @param row
	 *            Y
	 * @param distance
	 */
	private void updateSearchMatrixCaptain(int column, int row, int distance) {
		for (int j = row - distance; j < row + distance; j++) {
			for (int i = column - distance; i < column + distance; i++) {
				if (i >= 0 && j >= 0 && i < searchMatrix.length && j < searchMatrix.length) {
					searchMatrix[j][i] = 1;
				}
			}
		}
	}

	/**
	 * Updates the searchMatrix by report. The first two parameters should be
	 * the first position and the following values of the matrix.
	 * 
	 * @param report
	 *            String with the first position and the next values
	 */
	private void storeReport(String report) {
		String[] parts = report.split("_");
		int x = (int) Double.parseDouble(parts[0]);
		int y = (int) Double.parseDouble(parts[1]);

		for (int i = 2; i < parts.length; i++) {
			searchMatrix[y][x + i - 2] = (int) Double.parseDouble(parts[i]);
		}
	}

	/**
	 * Moves the Captain towards the point.
	 * 
	 * @param pt
	 */
	public void moveTowards(GridPoint pt) {
		if (!pt.equals(grid.getLocation(this))) {
			if (xCaptain > pt.getX()) {
				xCaptain--;
			} else if (xCaptain < pt.getX()) {
				xCaptain++;
			}

			if (yCaptain > pt.getY()) {
				yCaptain--;
			} else if (yCaptain < pt.getY()) {
				yCaptain++;
			}

			if (general != null) {
				space.moveTo(this, xCaptain, yCaptain);
				grid.moveTo(this, (int) xCaptain, (int) yCaptain);
			}
		}
	}
}
