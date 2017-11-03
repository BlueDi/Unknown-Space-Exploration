package unknownexplorer;

import java.util.Random;

import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import sajas.core.Agent;
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
