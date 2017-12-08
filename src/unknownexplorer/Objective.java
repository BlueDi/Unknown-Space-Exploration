package unknownexplorer;

import java.util.Random;

public class Objective {
	private double xObjective;
	private double yObjective;

	public Objective() {
		Random r = new Random();
		double[] randomNumbers = r.doubles(2, 0, 101).toArray();
		xObjective = randomNumbers[0];
		yObjective = randomNumbers[1];
	}

	public String toString() {
		return "Objective, [" + xObjective + ", " + yObjective + "]";
	}
}
