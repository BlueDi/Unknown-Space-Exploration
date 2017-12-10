package unknownexplorer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

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

	private AID general;
	private boolean ready;
	private double xCaptain;
	private double yCaptain;
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
	public Captain(ContinuousSpace<Object> space, Grid<Object> grid, int BOARD_DIM) {
		this.space = space;
		this.grid = grid;
		this.ready = false;
		searchMatrix = new int[BOARD_DIM][BOARD_DIM];
	}

	/**
	 * Initialize the Captain.
	 */
	protected void setup() {
		communicationRadius = 5;

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
		System.err.println("Captain " + getAID().getName() + " is ready.");
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
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchConversationId("position_to_search"),
					MessageTemplate.MatchConversationId("new_occupied_zone"));
			ACLMessage message = myAgent.receive(mt);
			if (message != null) {
				ACLMessage reply = message.createReply();
				if (message.getSender().getName().startsWith("Soldier")
						&& message.getConversationId() == "position_to_search") {
					if (!message.getContent().isEmpty())
						storeReport(message.getContent());

					if (ready && checkNearFreePositions()) {
						reply.setPerformative(ACLMessage.PROPOSE);
						reply.setContent(getSearchInfo());
					} else if (ready && !checkSoldierWorking()) {
						reply.setPerformative(ACLMessage.REFUSE);
						addBehaviour(new MoveToAnotherZone());
					} else {
						reply.setPerformative(ACLMessage.REFUSE);
					}
				} else if (general == null && message.getSender().getName().startsWith("Captain")
						&& message.getConversationId() == "new_occupied_zone"
						&& message.getPerformative() == ACLMessage.PROPOSE) {
					System.out.println(myAgent.getAID() + " " + message);
					String msg = message.getContent();
					String[] parts = msg.split("_");
					int[] point = new int[3];
					point[0] = Integer.parseInt(parts[0]);
					point[1] = Integer.parseInt(parts[1]);
					point[2] = Integer.parseInt(parts[2]);
					System.out.println(point[0] + " - " + Arrays.toString(searchMatrix[point[1]]));
					if (searchMatrix[point[1]][point[0]] == 0) {
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
					System.out.println(myAgent.getAID() + " " + message);
					String msg = message.getContent();
					String[] parts = msg.split("_");
					int[] point = new int[3];
					point[0] = Integer.parseInt(parts[0]);
					point[1] = Integer.parseInt(parts[1]);
					point[2] = Integer.parseInt(parts[2]);

					System.out.println(point[0] + " - " + Arrays.toString(searchMatrix[point[1]]));
					xCaptain = point[0];
					yCaptain = point[1];
					space.moveTo(myAgent, xCaptain, yCaptain);
					grid.moveTo(myAgent, (int) xCaptain, (int) yCaptain);

					updateSearchMatrixCaptain(point[0], point[1], point[2]);
					System.out.println(point[0] + " - " + Arrays.toString(searchMatrix[point[1]]));
					reply.setPerformative(ACLMessage.UNKNOWN);
				} else if (message.getSender().getName().startsWith("Captain")
						&& message.getConversationId() == "new_occupied_zone"
						&& message.getPerformative() == ACLMessage.CANCEL) {
					reply.setPerformative(ACLMessage.UNKNOWN);
					System.out.println(myAgent.getAID() + " " + message);
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

			boolean found = false;
			//boolean valid = true;
			int i = 0;// (int) (xCaptain - communicationRadius);
			int j = 0; // (int) (yCaptain - communicationRadius);
			int xFirst0 = -1;
			int yFirst0 = -1;
			int nPositions = 2 * communicationRadius;
			int xCounter = 0;
			int yCounter = 0;
			for (; j < searchMatrix.length; j++) {
				int tempCounter = 0;
				for (; i < searchMatrix.length; i++) {
					if (searchMatrix[j][i] == 0) {
						if (xFirst0 == -1 && yFirst0 == -1) {
							xFirst0 = i;
							yFirst0 = j;
						}
						tempCounter++;
						found = true;
					} else {
						break;
					}

					if (tempCounter >= nPositions) {
						//valid = false;
						break;
					}
				}
				
				if (found) {
					if(xCounter == 0)
						xCounter = tempCounter;
					else if (tempCounter < xCounter) {
						xCounter = tempCounter;
					}
					yCounter++;
					found = false;
				}else {
					break;
				}
				if (yCounter >= nPositions)
					break;
			}

			nPositions = (xCounter >= yCounter) ? yCounter : xCounter;

			System.out.println("Pos : " + xFirst0 + "-" + yFirst0 + ", Counters: " + xCounter + "-" + yCounter);
			System.out.println(myAgent.getLocalName() + "-" + (xFirst0 + (nPositions / 2)) + "-"
					+ (yFirst0 + (nPositions / 2)) + ": " + Arrays.toString(searchMatrix[0]));

			newZoneMessage
					.setContent((xFirst0 + (nPositions)) + "_" + (yFirst0 + (nPositions)) + "_" + nPositions);

			System.out.println(newZoneMessage.getContent());

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
			// TODO Auto-generated method stub
			return false;
		}
	}

	/**
	 * The Captain is always listening to other captains information about the
	 * goal.
	 */
	private class ListenBroadcastGoal extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchConversationId("broadcast_goal");
			ACLMessage reply = myAgent.receive(mt);

			try {
				String msg = reply.getContent();
				String[] parts = msg.split("_");
				int[] point = new int[2];
				point[0] = Integer.parseInt(parts[0]);
				point[1] = Integer.parseInt(parts[1]);
				goal = new GridPoint(point);
			} catch (NullPointerException e) {
			}
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
	 * Checks if there are free positions left on the area.
	 * 
	 * @return True if there are
	 */
	private boolean checkFreePositions() {
		boolean found = false;
		for (int[] row : searchMatrix) {
			found = IntStream.of(row).anyMatch(x -> x == 0);
			if (found) {
				break;
			}
		}
		return found;
	}

	/**
	 * Checks if there are free positions on the captain area.
	 * 
	 * @return True if there are
	 */
	private boolean checkNearFreePositions() {
		boolean found = false;
		int init = (int) yCaptain - communicationRadius;
		if (init < 0)
			init = 0;

		for (int j = init; j < searchMatrix.length && j < yCaptain + communicationRadius; j++) {
			int first = (int) xCaptain - communicationRadius;
			int last = (int) (xCaptain + communicationRadius);

			if (first < 0) {
				first = 0;
			}
			if (last > searchMatrix.length) {
				last = searchMatrix.length;
			}

			found = IntStream.of(Arrays.copyOfRange(searchMatrix[j], first, last)).anyMatch(x -> x == 4);
			if (found) {
				break;
			}
		}
		return found;
	}

	/**
	 * Checks if there are Soldiers working on the Captain area.
	 * 
	 * @return True if there are
	 */
	private boolean checkSoldierWorking() {
		boolean foundSoldierWorking = false;
		for (int j = (int) yCaptain; j < searchMatrix.length && j < yCaptain + communicationRadius; j++) {
			int last = (int) (xCaptain + communicationRadius);
			if (last < searchMatrix.length) {
				foundSoldierWorking = IntStream.of(Arrays.copyOfRange(searchMatrix[j], (int) xCaptain, last))
						.anyMatch(x -> x == 1);
			} else {
				foundSoldierWorking = IntStream
						.of(Arrays.copyOfRange(searchMatrix[j], (int) xCaptain, searchMatrix.length))
						.anyMatch(x -> x == 1);
			}
			if (foundSoldierWorking) {
				break;
			}
		}
		return foundSoldierWorking;
	}

	/**
	 * Calculates where the Soldier will start searching.
	 * 
	 * @return String with position where to start and the distance
	 */
	private String getSearchInfo() {
		boolean found = false;
		int i = (int) xCaptain - communicationRadius;
		int j = (int) yCaptain - communicationRadius;
		int counter = 0;

		if (i < 0)
			i = 0;
		if (j < 0)
			j = 0;

		for (; i < searchMatrix.length && i < xCaptain + communicationRadius; i++) {
			for (; j < searchMatrix.length && j < yCaptain + communicationRadius; j++) {
				if (searchMatrix[j][i] == 0) {
					found = true;
					break;
				}
			}
			if (found) {
				break;
			}
		}

		if (found) {
			for (int k = i; k < searchMatrix.length; k++) {
				if (searchMatrix[j][k] == 0 && (2 * communicationRadius) - counter > 0) {
					counter++;
				} else {
					break;
				}
			}
		}

		updateSearchMatrixSoldier(i, j, counter);

		return i + "_" + j + "_" + counter;
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
			} else {
				xCaptain++;
			}

			if (yCaptain > pt.getY()) {
				yCaptain--;
			} else {
				yCaptain++;
			}

			if (general != null) {
				space.moveTo(this, xCaptain, yCaptain);
				grid.moveTo(this, (int) xCaptain, (int) yCaptain);
			}
		}
	}
}
