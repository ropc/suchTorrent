/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
public class Bitfield {
	private byte[] array;
	public int length;

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

	public static decode(byte[] array, int numBits) {
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

	public static byte And(byte a, byte b) {
		return a & b;
	}

	public static Bitfield BitfieldAND(Bitfield b1, Bitfield b2) {
		Bitfield resultBitfield = null;
		if (b1.length == b2.length) {
			resultBitfield = new Bitfield(b1.length);
			for (int i = 0; i < b1.length; i++) {
				resultBitfield.array[i] = b1.array[i] & b2.array[i];
			}
		}
		return resultBitfield;
	}
}