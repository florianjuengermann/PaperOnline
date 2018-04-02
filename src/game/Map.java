package game;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Map {
	/**
	 * NOT thread-safe!
	 */
	public byte[][] map;
	final int w;
	private GameModel gm;

	public Map(int w, GameModel gm) {
		this.w = w;
		map = new byte[w][w];
		this.gm = gm;
	}

	public void fill(byte pid /*player id*/) {
		int[][] buff = new int[w][w];
		// because there could be many clusters, fids can be bigger than 127..-> int
		int fid = 1; // fill id
		List<Boolean> clustersOK = new ArrayList<>();
		clustersOK.add(false); // just to have first item at index 1
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < w; j++) {
				if (buff[i][j] == 0 && Math.abs(map[i][j]) != pid) { // wasnt here yet
					clustersOK.add(floodhere(fid, pid, i, j, buff));
					fid++;
				}

			}
		}
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < w; j++) {
				if (clustersOK.get(buff[i][j]) /*cluster belongs to player*/ ||
						map[i][j] == -pid /*player's trail*/) {
					map[i][j] = pid;
				}
			}
		}
	}


	public void spawn(byte pid, int pX, int pY) {
		//gets called by main thread, TODO thread-safty
		for (int x = pX - 1; x <= pX + 1; ++x) {
			for (int y = pY - 1; y <= pY + 1; ++y) {
				map[x][y] = pid;
			}
		}
	}

	private boolean floodhere(int fid, int pid, int i, int j, int[][] buff) {
		Stack<int[]> s = new Stack<>();
		s.push(new int[]{i, j});
		boolean clusterOK = true;
		while (!s.isEmpty()) {
			int[] curr = s.pop();
			int k = curr[0], l = curr[1];
			buff[k][l] = fid;
			int m, n;

			m = k - 1;
			n = l;
			if (m < 0 || m == w || n < 0 || n == w) {
				// border
				clusterOK = false;
			} else if (buff[m][n] == 0 /*not there yet*/ &&
					Math.abs(map[m][n]) != pid /*"wall"*/) {
				s.push(new int[]{m, n});
			}

			m = k + 1;
			n = l;
			if (m < 0 || m == w || n < 0 || n == w) {
				// border
				clusterOK = false;
			} else if (buff[m][n] == 0 /*not there yet*/ &&
					Math.abs(map[m][n]) != pid /*"wall"*/) {
				s.push(new int[]{m, n});
			}

			m = k;
			n = l - 1;
			if (m < 0 || m == w || n < 0 || n == w) {
				// border
				clusterOK = false;
			} else if (buff[m][n] == 0 /*not there yet*/ &&
					Math.abs(map[m][n]) != pid /*"wall"*/) {
				s.push(new int[]{m, n});
			}

			m = k;
			n = l + 1;
			if (m < 0 || m == w || n < 0 || n == w) {
				// border
				clusterOK = false;
			} else if (buff[m][n] == 0 /*not there yet*/ &&
					Math.abs(map[m][n]) != pid /*"wall"*/) {
				s.push(new int[]{m, n});
			}
		}
		return clusterOK;
	}

	void remove(int id) {
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < w; j++) {
				if (Math.abs(map[i][j]) == id) {
					map[i][j] = 0;
				}
			}
		}
	}


}
