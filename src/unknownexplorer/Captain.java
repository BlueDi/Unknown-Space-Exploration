package unknownexplorer;

import java.util.Arrays;
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
		searchMatrix = new int[BOARD_DIM][BOARD_DIM];
	}

	/**
	 * Initialize the Captain.
	 */
	protected void setup() {
		xCaptain = searchMatrix.length / 2;
		yCaptain = xCaptain;

		communicationRadius = 5;

		space.moveTo(this, xCaptain, yCaptain);
		grid.moveTo(this, (int) xCaptain, (int) yCaptain);

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

					if (checkNearFreePositions()) {
						reply.setPerformative(ACLMessage.PROPOSE);
						reply.setContent(getSearchInfo());
					} else {
						reply.setPerformative(ACLMessage.REFUSE);
					}
				} else if (message.getSender().getName().startsWith("Captain")
						&& message.getConversationId() == "new_occupied_zone") {
					String msg = reply.getContent();
					String[] parts = msg.split("_");
					int[] point = new int[2];
					point[0] = Integer.parseInt(parts[0]);
					point[1] = Integer.parseInt(parts[1]);
					point[2] = Integer.parseInt(parts[2]);
					updateSearchMatrix(point[1], point[0], point[2]);
					reply.setPerformative(ACLMessage.CONFIRM);
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

			int i = 0;
			int j = 0;
			while (searchMatrix[j][i] != 0 && i != searchMatrix.length) {
				j++;
				if (j == searchMatrix.length) {
					j = 0;
					i++;
				}
			}

			if (i != searchMatrix.length) {
				newZoneMessage.setContent(j + "_" + i + "_" + communicationRadius);
				xCaptain = j;
				yCaptain = i;

				space.moveTo(myAgent, xCaptain, yCaptain);
				grid.moveTo(myAgent, (int) xCaptain, (int) yCaptain);
			} else {
				newZoneMessage.setContent(-1 + "_" + -1 + "_" + communicationRadius);
			}

			myAgent.send(newZoneMessage);
		}

		@Override
		public boolean done() {
			// TODO Auto-generated method stub
			return false;
		}
	}

	/**
	 * When the captain receives information about the goal from one of his soldiers
	 * he will broadcast the goal position to the other captains.
	 */
	private class BroadcastGoal extends Behaviour {
		private static final long serialVersionUID = 1L;
		private AID[] allCaptains;

		@Override
		public void action() {
			// Update the list of Captains
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Comunication");
			template.addServices(sd);
			try {
				DFAgentDescription[] result = DFService.search(myAgent, template);
				System.out.println(getAID().getName() + " found " + result.length + " captains.");
				allCaptains = new AID[result.length];
				for (int i = 0; i < result.length; ++i) {
					allCaptains[i] = result[i].getName();
				}
			} catch (FIPAException fe) {
				fe.printStackTrace();
			}

			// Send the goal to all Captains
			ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
			for (int i = 0; i < allCaptains.length; ++i) {
				cfp.addReceiver(allCaptains[i]);
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
	 * The captain is always listening to other captains information about the goal.
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
		for (int i = (int) yCaptain; i < searchMatrix.length && i < yCaptain + communicationRadius; i++) {
			int last = (int) (xCaptain + communicationRadius);
			if (last < searchMatrix.length) {
				found = IntStream.of(Arrays.copyOfRange(searchMatrix[i], (int) xCaptain, last)).anyMatch(x -> x == 0);
			} else {
				found = IntStream.of(Arrays.copyOfRange(searchMatrix[i], (int) xCaptain, searchMatrix.length))
						.anyMatch(x -> x == 0);
			}
			if (found) {
				break;
			}
		}
		return found;
	}

	/**
	 * Calculates where the Soldier will start searching.
	 * 
	 * @return String with position where to start and the distance
	 */
	private String getSearchInfo() {
		boolean found = false;
		int i = (int) xCaptain;
		int j = (int) yCaptain;
		int counter = 0;

		for (; j < searchMatrix.length && j < yCaptain + communicationRadius; j++) {
			for (; i < searchMatrix.length && i < xCaptain + communicationRadius; i++) {
				if (searchMatrix[i][j] == 0) {
					found = true;
					break;
				}
			}
			if (found) {
				break;
			}
		}

		if (found) {
			for (; i < searchMatrix.length; i++) {
				if (searchMatrix[i][j] == 0 && communicationRadius - counter > 0) {
					counter++;
				} else {
					break;
				}
			}
		} else {
			addBehaviour(new MoveToAnotherZone());
		}

		updateSearchMatrix(i, j, counter);

		return i + "_" + j + "_" + counter;
	}

	/**
	 * Updates the searchMatrix.
	 * 
	 * @param column
	 * @param row
	 * @param distance
	 */
	private void updateSearchMatrix(int column, int row, int distance) {
		for (int i = 0; i < distance; i++) {
			searchMatrix[column][row] = 1;
		}
	}

	/**
	 * Updates the searchMatrix by report. The first two parameters should be the
	 * first position and the following values of the matrix.
	 * 
	 * @param report
	 *            String with the first position and the next values
	 */
	private void storeReport(String report) {
		String[] parts = report.split("_");
		int x = (int) Double.parseDouble(parts[0]);
		int y = (int) Double.parseDouble(parts[1]);

		if (x != -1) {
			for (int i = 2; i < parts.length; i++) {
				searchMatrix[y][x + i - 2] = (int) Double.parseDouble(parts[i]);
			}
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

			space.moveTo(this, xCaptain, yCaptain);
			grid.moveTo(this, (int) xCaptain, (int) yCaptain);
		}
	}
}
