import java.util.Comparator;

public class PieceWithIndex {
	final int index;
	final int peerCount;

	public PieceWithIndex(int index, int peerCount) {
		this.index = index;
		this.peerCount = peerCount;
	}

	@Override
	public boolean equals(Object obj) {
		return this.index == ((PieceWithIndex)obj).index;
	}

	public String toString() {
		return new String("(" + index + ", "+ peerCount + ")");
	}

	public static class PiecePriorityComparator implements Comparator<PieceWithIndex> {
		public int compare(PieceWithIndex o1, PieceWithIndex o2) {
			return o1.peerCount - o2.peerCount;
		}
	}
}