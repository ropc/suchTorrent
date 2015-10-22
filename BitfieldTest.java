public class BitfieldTest {
	public static void main(String[] args) {
		// a couple new 16 bit bitfields
		Bitfield myBitfield = new Bitfield(16);
		Bitfield otherBitfield = new Bitfield(16);
		// myBitfield.print();
		// otherBitfield.print();

		myBitfield.set(2);

		myBitfield.And(otherBitfield).print();
		myBitfield.Or(otherBitfield).print();
		myBitfield.Xor(otherBitfield).print();
		Bitfield notMyBitfield = myBitfield.Not();
		notMyBitfield.print();
		notMyBitfield.unset(4);
		notMyBitfield.print();
		// myBitfield.print();
		// byte[] suchArray = myBitfield.getArray();
		// suchArray[0] = -8;
		// myBitfield.print();
	}
}