package unknownexplorer;

import java.util.List;
import java.util.Random;

import jade.core.AID;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
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
import sajas.core.behaviours.TickerBehaviour;
import sajas.domain.DFService;

public class Soldier extends Agent {
	private ContinuousSpace<Object> space;
	private Grid<Object> grid;

	private AID[] allCaptains;

	private double xSoldier;
	private double ySoldier;
	private double velocitySoldier;

	private boolean hasInfo;
	private String info;
	
	private int x;
	private int y;

	public Soldier(ContinuousSpace<Object> space, Grid<Object> grid, int x, int y) {
		this.space = space;
		this.grid = grid;
		hasInfo = false;
		this.x =x;
		this.y =y;
	}

	protected void setup() {
		Random r = new Random();
		double[] randomNumbers = r.doubles(2, 0, 101).toArray();
		xSoldier = this.x;//randomNumbers[0];
		ySoldier = this.y;//randomNumbers[1];
		velocitySoldier = 2;

		space.moveTo(this, xSoldier, ySoldier);
		grid.moveTo(this, (int) xSoldier, (int) ySoldier);

		addBehaviour(new SearchCaptains(this, 1));
		addBehaviour(new SearchGoal());

		System.out.println("Soldier " + getAID().getName() + " is ready.");
	}

	protected void takeDown() {
		System.out.println("Soldier " + getAID().getName() + " terminating.");
	}

	private class SearchCaptains extends TickerBehaviour {
		public SearchCaptains(Agent a, long period) {
			super(a, period);
		}

		private static final long serialVersionUID = 1L;

		protected void onTick() {
			// Update the list of Humans
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

			// Perform the request
			myAgent.addBehaviour(new SoldierBehaviour());
		}
	}

	private class SoldierBehaviour extends Behaviour {
		private static final long serialVersionUID = 1L;
		private AID closestCaptain;
		private double bestDistance;
		private double bestCaptainX;
		private double bestCaptainY;
		private int captainsCounter = 0;
		private MessageTemplate mt;
		private int step = 0;

		public void action() {
			
			switch (step) {
			case 0:
				// Send the cfp to all Captains
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < allCaptains.length; ++i) {
					cfp.addReceiver(allCaptains[i]);
				}
				cfp.setContent(xSoldier + "_" + ySoldier);
				cfp.setConversationId("comns");
				cfp.setReplyWith("cfp" + System.currentTimeMillis());
				myAgent.send(cfp);

				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("comns"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					boolean isNear = false;
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						isNear = true;
					} else if (reply.getPerformative() == ACLMessage.REFUSE) {
						String msg = reply.getContent();
						String[] parts = msg.split("_");
						double xCaptain = Double.parseDouble(parts[0]);
						double yCaptain = Double.parseDouble(parts[1]);

						double distance = Math
								.sqrt(Math.pow(xCaptain - xSoldier, 2) + Math.pow(yCaptain - ySoldier, 2));

						if (closestCaptain == null || distance < bestDistance) {
							bestDistance = distance;
							closestCaptain = reply.getSender();
							bestCaptainX = xCaptain;
							bestCaptainY = yCaptain;
						}
					}

					captainsCounter++;
					if (hasInfo && isNear) {
						step = 2;
					} else if (captainsCounter >= allCaptains.length && hasInfo && !isNear) {
						if (bestCaptainX > xSoldier)
							xSoldier += velocitySoldier;
						else if (bestCaptainX < xSoldier)
							xSoldier -= velocitySoldier;

						if (bestCaptainY > ySoldier)
							ySoldier += velocitySoldier;
						else if (bestCaptainY < ySoldier)
							ySoldier -= velocitySoldier;

						space.moveTo(myAgent, xSoldier, ySoldier);
						grid.moveTo(myAgent, (int) xSoldier, (int) ySoldier);

						step = 0;
					} 
					// TO DO
					else if (captainsCounter >= allCaptains.length && !hasInfo) {
						Random r = new Random();
						double[] randomNumbers = r.doubles(2, -velocitySoldier, velocitySoldier).toArray();
						
						xSoldier += 2;
						ySoldier += 0;
						//xSoldier += randomNumbers[0];
						//ySoldier += randomNumbers[1];
						step = 0;
					}

					space.moveTo(myAgent, xSoldier, ySoldier);
					grid.moveTo(myAgent, (int) xSoldier, (int) ySoldier);
				} else {
					block();
				}
				break;

			case 2:
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(closestCaptain);
				// TODO: setContent with the information
				order.setContent(xSoldier + "_" + ySoldier + "_" + info);
				System.err.println(info);
				order.setConversationId("comns");
				order.setReplyWith("info" + System.currentTimeMillis());
				myAgent.send(order);

				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("comns"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:
				reply = myAgent.receive(mt);
				if (reply != null) {
					if (reply.getPerformative() == ACLMessage.INFORM) {
						System.out.println("Successfully informed " + reply.getSender().getName());
						hasInfo = false;
					} else if (reply.getPerformative() == ACLMessage.FAILURE) {
						System.out.println("Failed to inform " + reply.getSender().getName());
						hasInfo = true;
					}

					step = 1;
				} else {
					block();
				}
				break;
			}
		}

		public boolean done() {
			// TODO: Check if reached the objective
			return false;
		}
	}

	// GOAL SEARCH
	private class SearchGoal extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		@Override
		public void action() {
			GridPoint pt = grid.getLocation(myAgent);

			GridCellNgh<Objective> nghCreator = new GridCellNgh<Objective>(grid, pt, Objective.class, 1, 1);
			List<GridCell<Objective>> gridCells = nghCreator.getNeighborhood(true);

			GridPoint goal = null;
			for (GridCell<Objective> cell : gridCells) {
				if (cell.size() > 0) {
					goal = cell.getPoint();
					hasInfo = true;
					info = goal.getX() + "_" + goal.getY();
				}
			}
		}

	}
}
