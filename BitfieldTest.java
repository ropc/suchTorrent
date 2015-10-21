public class BitfieldTest {
	public static void main(String[] args) {
		// a couple new 16 bit bitfields
		Bitfield myBitfield = new Bitfield(16);
		Bitfield otherBitfield = new Bitfield(16);
		myBitfield.print();
		otherBitfield.print();

		myBitfield.And(otherBitfield).print();
		myBitfield.Or(otherBitfield).print();
		myBitfield.Xor(otherBitfield).print();
		myBitfield.Not().print();
	}
}