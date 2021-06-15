package org.bouncycastle.asn1;

import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;

/**
 * Base class for an ASN.1 Private object
 */
public abstract class ASN1Private
    extends ASN1Primitive
{
    protected final boolean   isConstructed;
    protected final int       tag;
    protected final byte[]    octets;

    ASN1Private(
        boolean isConstructed,
        int tag,
        byte[] octets)
    {
        this.isConstructed = isConstructed;
        this.tag = tag;
        this.octets = Arrays.clone(octets);
    }

    /**
     * Return an ASN1Private from the passed in object, which may be a byte array, or null.
     *
     * @param obj the object to be converted.
     * @return obj's representation as an ASN1Private object.
     */
    public static ASN1Private getInstance(Object obj)
    {
        if (obj == null || obj instanceof ASN1Private)
        {
            return (ASN1Private)obj;
        }
        else if (obj instanceof byte[])
        {
            try
            {
                return ASN1Private.getInstance(ASN1Primitive.fromByteArray((byte[])obj));
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Failed to construct object from byte[]: " + e.getMessage());
            }
        }

        throw new IllegalArgumentException("unknown object in getInstance: " + obj.getClass().getName());
    }

    protected static int getLengthOfHeader(byte[] data)
    {
        int length = data[1] & 0xff; // TODO: assumes 1 byte tag

        if (length == 0x80)
        {
            return 2;      // indefinite-length encoding
        }

        if (length > 127)
        {
            int size = length & 0x7f;

            // Note: The invalid long form "0xff" (see X.690 8.1.3.5c) will be caught here
            if (size > 4)
            {
                throw new IllegalStateException("DER length more than 4 bytes: " + size);
            }

            return size + 2;
        }

        return 2;
    }

    /**
     * Return true if the object is marked as constructed, false otherwise.
     *
     * @return true if constructed, otherwise false.
     */
    public boolean isConstructed()
    {
        return isConstructed;
    }

    /**
     * Return the contents of this object as a byte[]
     *
     * @return the encoded contents of the object.
     */
    public byte[] getContents()
    {
        return Arrays.clone(octets);
    }

    /**
     * Return the tag number associated with this object,
     *
     * @return the application tag number.
     */
    public int getPrivateTag() 
    {
        return tag;
    }

    /**
     * Return the enclosed object assuming explicit tagging.
     *
     * @return  the resulting object
     * @throws IOException if reconstruction fails.
     */
    public ASN1Primitive getObject()
        throws IOException 
    {
        return ASN1Primitive.fromByteArray(getContents());
    }

    /**
     * Return the enclosed object assuming implicit tagging.
     *
     * @param derTagNo the type tag that should be applied to the object's contents.
     * @return  the resulting object
     * @throws IOException if reconstruction fails.
     */
    public ASN1Primitive getObject(int derTagNo)
        throws IOException
    {
        if (derTagNo >= 0x1f)
        {
            throw new IOException("unsupported tag number");
        }

        byte[] orig = this.getEncoded();
        byte[] tmp = replaceTagNumber(derTagNo, orig);

        if ((orig[0] & BERTags.CONSTRUCTED) != 0)
        {
            tmp[0] |= BERTags.CONSTRUCTED;
        }

        return ASN1Primitive.fromByteArray(tmp);
    }

    boolean asn1Equals(
        ASN1Primitive o)
    {
        if (!(o instanceof ASN1Private))
        {
            return false;
        }

        ASN1Private other = (ASN1Private)o;

        return isConstructed == other.isConstructed
            && tag == other.tag
            && Arrays.areEqual(octets, other.octets);
    }

    public int hashCode()
    {
        return (isConstructed ? 1 : 0) ^ tag ^ Arrays.hashCode(octets);
    }

    private byte[] replaceTagNumber(int newTag, byte[] input)
        throws IOException
    {
        int tagNo = input[0] & 0x1f;
        int index = 1;
        //
        // with tagged object tag number is bottom 5 bits, or stored at the start of the content
        //
        if (tagNo == 0x1f)
        {
            int b = input[index++] & 0xff;

            // X.690-0207 8.1.2.4.2
            // "c) bits 7 to 1 of the first subsequent octet shall not all be zero."
            if ((b & 0x7f) == 0) // Note: -1 will pass
            {
                throw new IOException("corrupted stream - invalid high tag number found");
            }

            while ((b & 0x80) != 0)
            {
                b = input[index++] & 0xff;
            }
        }

        byte[] tmp = new byte[input.length - index + 1];

        System.arraycopy(input, index, tmp, 1, tmp.length - 1);

        tmp[0] = (byte)newTag;

        return tmp;
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        if (isConstructed())
        {
            sb.append("CONSTRUCTED ");
        }
        sb.append("PRIVATE ");
        sb.append(Integer.toString(getPrivateTag()));
        sb.append("]");
        // @todo content encoding somehow?
        if (this.octets != null)
        {
            sb.append(" #");
            sb.append(Hex.toHexString(this.octets));
        }
        else
        {
            sb.append(" #null");
        }
        sb.append(" ");
        return sb.toString();
    }
}