/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */

public class PieceIndexCount implements Comparable<PieceIndexCount> {
	public final int index;
	public final int peerCount;

	public PieceIndexCount(int index, int peerCount) {
		this.index = index;
		this.peerCount = peerCount;
	}

	@Override
	public boolean equals(Object obj) {
		return this.index == ((PieceIndexCount)obj).index;
	}

	public String toString() {
		return new String("(" + index + ", "+ peerCount + ")");
	}

	public int compareTo(PieceIndexCount o) {
		return this.peerCount - o.peerCount;
	}
}