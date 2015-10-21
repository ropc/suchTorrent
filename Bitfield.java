/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
public class Bitfield {
	private	 byte[] array;
	public final int length;

	public Bitfield(int numBits) {
		if (numBits > 0) {
			int numBytes = numBits / 8;
			// add one if 1-7, 9-15,... bits
			if (numBits % 8 > 0)
				numBytes++;

			array = new byte[numBytes];
		} else {
			array = null;
		}
		length = numBits;
		// System.out.println("Created bitfield wtih " + length + " bits.");
	}

	/**
	 * This constructor should only be called by decode()
	 * @param  array   initialized byte array describing the bitfield
	 * @param  numBits number of bits useful in array
	 */
	private Bitfield(byte[] array, int numBits) {
		this.array = array;
		length = numBits;
	}

	/**
	 * Mostly for debugging, will print the bits in one line.
	 */
	public void print() {
		System.out.print("Length: " + length + " Bits: ");
		int numBytes = length / 8;
		if (length % 8 > 0)
			numBytes++;

		for (int i = 0; i < numBytes; i++) {
			for (int j = 7; j >= 0; j--) {
				if (((array[i] >> j) & 1) == 1)
					System.out.print("1");
				else
					System.out.print("0");
			}
			System.out.print(" ");
		}
		System.out.println();
	}

	public static Bitfield decode(byte[] array, int numBits) {
		int numBytes = numBits / 8;
		if (numBits % 8 > 0)
			numBytes++;

		// if the given array is the right length, return a Bitfield
		// with describing this array
		if (numBits > 0 && array.length == numBytes) {
			return new Bitfield(array, numBits);
		} else {
			return null;
		}
	}

	public Bitfield And(Bitfield otherBitfield) {
		return BitfieldAND(this, otherBitfield);
	}

	public static Bitfield BitfieldAND(Bitfield b1, Bitfield b2) {
		return BitfieldOperation(b1, b2, new AND());
	}

	public static Bitfield BitfieldOperation(Bitfield b1, Bitfield b2, BitwiseFunction func) {
		Bitfield resultBitfield = null;
		if (b1.length == b2.length) {
			resultBitfield = new Bitfield(b1.length);
			for (int i = 0; i < b1.length; i++) {
				resultBitfield.array[i] = func.execute(b1.array[i], b2.array[i]);
			}
		}
		return resultBitfield;
	}

	public interface BitwiseFunction {
		public byte execute(byte a, byte b);
	}

	public static final class AND implements BitwiseFunction {
		public byte execute(byte a, byte b) {
			return (byte)(a & b);
		}
	}

	public static final class OR implements BitwiseFunction {
		public byte execute(byte a, byte b) {
			return (byte)(a | b);
		}
	}

	public static final class XOR implements BitwiseFunction {
		public byte execute(byte a, byte b) {
			return (byte)(a ^ b);
		}
	}
}