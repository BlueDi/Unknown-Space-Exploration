package unknownexplorer;

import java.util.Random;

public class Wall {
	private double xWall;
	private double yWall;

	public Wall(int dim) {
		Random r = new Random();
		double[] randomNumbers = r.doubles(2, 0, dim).toArray();
		xWall = randomNumbers[0];
		yWall = randomNumbers[1];
	}

	public Wall(double x, double y) {
		xWall = x;
		yWall = y;
	}

	public double getX() {
		return xWall;
	}

	public double getY() {
		return yWall;
	}

	public String toString() {
		return "Wall, [" + xWall + ", " + yWall + "]";
	}
}
