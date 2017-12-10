package unknownexplorer;

import java.lang.reflect.Field;
import java.util.Random;

public class Objective {
	private double xObjective;
	private double yObjective;

	public Objective() {
		//Random r = new Random();
		//double[] randomNumbers = r.doubles(2, 0, 101).toArray();
		xObjective = 0;// randomNumbers[0];
		yObjective = 0;//randomNumbers[1];
	}

	public String toString() {
		return "Objective, [" + xObjective + ", " + yObjective + "]";
	}

	public double getDeclaredField(String string) {
		if (string == "xObjective"){
			return xObjective;
		}
		else return yObjective;
	}
}
