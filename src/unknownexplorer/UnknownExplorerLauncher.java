package unknownexplorer;

import java.util.ArrayList;
import java.util.List;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.StaleProxyException;
import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;
import repast.simphony.space.grid.StrictBorders;
import sajas.core.AID;
import sajas.core.Agent;
import sajas.core.Runtime;
import sajas.sim.repasts.RepastSLauncher;
import sajas.wrapper.ContainerController;

public class UnknownExplorerLauncher extends RepastSLauncher {
	private static int BOARD_DIM;
	private static int N_SOLDIERS;
	private static int N_CAPTAINS;
	private static int COMMUNICATION_RADIUS;
	private static double VISION_RADIUS = 0.1 * BOARD_DIM;

	private ContinuousSpace<Object> space;
	private Grid<Object> grid;

	private ContainerController mainContainer;

	public static Agent getAgent(Context<?> context, AID aid) {
		for (Object obj : context.getObjects(Agent.class)) {
			if (((Agent) obj).getAID().equals(aid)) {
				return (Agent) obj;
			}
		}
		return null;
	}

	public static int getN_SOLDIERS() {
		return N_SOLDIERS;
	}

	public static void setN_SOLDIERS(int n_SOLDIERS) {
		N_SOLDIERS = n_SOLDIERS;
	}

	public static int getN_CAPTAINS() {
		return N_CAPTAINS;
	}

	public static void setN_CAPTAINS(int n_CAPTAINS) {
		N_CAPTAINS = n_CAPTAINS;
	}

	@Override
	public String getName() {
		return "SAJaSUnknownExplorer";
	}

	@Override
	protected void launchJADE() {
		Runtime rt = Runtime.instance();
		Profile p1 = new ProfileImpl();
		mainContainer = rt.createMainContainer(p1);

		launchAgents();
	}

	// AGENTS INIT POS
	private void launchAgents() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		N_CAPTAINS = params.getInteger("N_CAPTAINS") + 1;
		BOARD_DIM = params.getInteger("BOARD_DIM");
		N_SOLDIERS = params.getInteger("N_SOLDIERS");
		COMMUNICATION_RADIUS = params.getInteger("COMMUNICATION_RADIUS");
		VISION_RADIUS = params.getDouble("VISION_RADIUS");

		List<jade.core.AID> allCaptains = new ArrayList<jade.core.AID>();
		try {
			Captain[] caps = new Captain[N_CAPTAINS];
			for (int i = 0; i < N_CAPTAINS; i++) {
				Captain c = new Captain(space, grid, BOARD_DIM, COMMUNICATION_RADIUS);
				mainContainer.acceptNewAgent("Captain" + i, c).start();
				caps[i] = c;
				if (i != 0)
					c.setGeneral(caps[0].getAID());
				allCaptains.add(c.getAID());

				NdPoint pt = space.getLocation(c);
				grid.moveTo(c, 0, 0);
			}

			for (int i = 0; i < N_CAPTAINS; i++) {
				List<jade.core.AID> otherCaptains = new ArrayList<>();
				Captain c = caps[i];
				allCaptains.forEach(captainAID -> {
					if (captainAID != c.getAID())
						otherCaptains.add(captainAID);
				});
				c.setOtherCaptains(otherCaptains);
			}

			int counter = 1;
			for (int i = 0; i < N_SOLDIERS; i++) {
				Soldier s = new Soldier(space, grid, caps[counter].getAID(), VISION_RADIUS);
				mainContainer.acceptNewAgent("Soldier" + i, s).start();
				grid.moveTo(s, 0, 0);

				counter++;
				if (counter >= N_CAPTAINS) {
					counter = 1;
				}
			}
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Context<?> build(Context<Object> context) {
		context.setId("SAJaSUnknownExplorer");

		launchEnvironment(context);

		return super.build(context);
	}

	private void launchEnvironment(Context<Object> context) {
		Parameters params = RunEnvironment.getInstance().getParameters();
		int BOARD_DIM = params.getInteger("BOARD_DIM");
		int xdim = BOARD_DIM;
		int ydim = BOARD_DIM;
		ContinuousSpaceFactory spaceFactory = ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		space = spaceFactory.createContinuousSpace("space", context, new RandomCartesianAdder<Object>(),
				new repast.simphony.space.continuous.StrictBorders(), xdim, ydim);

		GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
		grid = gridFactory.createGrid("grid", context, new GridBuilderParameters<Object>(new StrictBorders(),
				new SimpleGridAdder<Object>(), true, xdim, ydim));

		// WALLS
		for (int i = 0; i < 9; i++) {
			Wall w = new Wall(9, i);
			context.add(w);
			grid.moveTo(w, 9, i);
			space.moveTo(w, 9, i);
		}
		for (int i = 0; i < 6; i++) {
			Wall w = new Wall(39, i);
			context.add(w);
			grid.moveTo(w, 39, i);
			space.moveTo(w, 39, i);
		}
		for (int i = 0; i < 6; i++) {
			Wall w = new Wall(38, i);
			context.add(w);
			grid.moveTo(w, 38, i);
			space.moveTo(w, 38, i);
		}

		// OBJ POINT
		Objective o = new Objective();
		context.add(o);
		double x = o.getDeclaredField("xObjective");
		double y = o.getDeclaredField("yObjective");
		NdPoint pd = new NdPoint(x, y);
		grid.moveTo(o, (int) pd.getX(), (int) pd.getY());
		space.moveTo(o, (int) pd.getX(), (int) pd.getY());
	}
}
