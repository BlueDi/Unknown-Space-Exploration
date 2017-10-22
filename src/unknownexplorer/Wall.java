package unknownexplorer;

import java.util.Random;

import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.grid.Grid;

public class Wall {

	private double xWall;
	private double yWall;

	public Wall() {
		Random r = new Random();
		double[] randomNumbers = r.doubles(2, 0, 101).toArray();
		xWall = randomNumbers[0];
		yWall = randomNumbers[1];
	}
}
