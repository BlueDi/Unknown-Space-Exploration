package unknownexplorer;

import java.util.Random;

public class Objective {
	private static double xObjective;
	private static double yObjective;

	public Objective() {
		Random r = new Random();
		double[] randomNumbers = r.doubles(2, 0, 49).toArray();
		setxObjective(randomNumbers[0]);
		setyObjective(randomNumbers[1]);
	}

	public String toString() {
		return "Objective, [" + getxObjective() + ", " + getyObjective() + "]";
	}

	public double getDeclaredField(String string) {
		if (string == "xObjective"){
			return getxObjective();
		}
		if(string == "yObjective"){
			return getyObjective();
		}
		else return 0;
	}

	public static double getxObjective() {
		return xObjective;
	}

	public static double getyObjective() {
		return yObjective;
	}
	
	public static void setxObjective(double xObjective) {
		Objective.xObjective = xObjective;
	}

	public static void setyObjective(double yObjective) {
		Objective.yObjective = yObjective;
	}
}
