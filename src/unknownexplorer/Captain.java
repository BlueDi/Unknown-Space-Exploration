package unknownexplorer;

import java.util.Random;

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

public class Captain extends Agent {
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;

	private double xCaptain;
	private double yCaptain;
	private GridPoint goal;
	private int communicationRadius;
	private int visionRadius;

	public Captain(ContinuousSpace<Object> space, Grid<Object> grid) {
		this.space = space;
		this.grid = grid;
	}

	protected void setup() {
		Random r = new Random();
		double[] randomNumbers = r.doubles(2, 0, 101).toArray();
		xCaptain = randomNumbers[0];
		yCaptain = randomNumbers[1];

		communicationRadius = 5;
		visionRadius = 5000;

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
		addBehaviour(new CaptainBehaviour());
		addBehaviour(new MoveBehaviour());
		System.err.println("Captain " + getAID().getName() + " is ready.");
	}

	protected void takeDown() {
		System.out.println("Captain " + getAID().getName() + " terminating.");
	}

	private class ExchangeInformation extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		/**
		 * The Soldier will send his position to the Captain. The captain will
		 * say if they are in range to exchange information.
		 */
		public void action() {
			ACLMessage message = myAgent.receive();
			if (message != null) {
				String msg = message.getContent();

				String[] parts = msg.split("_");
				double xSoldier = Double.parseDouble(parts[0]);
				double ySoldier = Double.parseDouble(parts[1]);

				double distance = Math.sqrt(Math.pow(xCaptain - xSoldier, 2) + Math.pow(yCaptain - ySoldier, 2));

				ACLMessage reply = message.createReply();
				if (distance <= communicationRadius) {
					reply.setPerformative(ACLMessage.PROPOSE);
				} else {
					reply.setPerformative(ACLMessage.REFUSE);
				}
				reply.setContent(xCaptain + "_" + yCaptain);
				myAgent.send(reply);
			}
		}
	}

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
			System.out.println(cfp.toString());
		}

		@Override
		public boolean done() {
			// TODO Auto-generated method stub
			return false;
		}
	}

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
			} catch (NullPointerException e) {}
		}
	}

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

	private class CaptainBehaviour extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage message = myAgent.receive(mt);
			if (message != null) {
				String msg = message.getContent();

				String[] parts = msg.split("_");
				double xSoldier = Double.parseDouble(parts[0]);
				double ySoldier = Double.parseDouble(parts[1]);

				double distance = Math.sqrt(Math.pow(xCaptain - xSoldier, 2) + Math.pow(yCaptain - ySoldier, 2));

				ACLMessage reply = message.createReply();
				if (distance <= communicationRadius) {
					// TODO: Receive the soldier information
					int[] point = new int[2];
					point[0] = Integer.parseInt(parts[2]);
					point[1] = Integer.parseInt(parts[3]);
					goal = new GridPoint(point);
					reply.setPerformative(ACLMessage.INFORM);
					System.out
							.println(getAID().getName() + " received information of " + message.getSender().getName());
					myAgent.addBehaviour(new BroadcastGoal());
				} else {
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			} else {
				block();
			}
		}
	}

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
