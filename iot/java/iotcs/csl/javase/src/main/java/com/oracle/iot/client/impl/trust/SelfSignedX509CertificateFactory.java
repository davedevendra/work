/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates.  All rights reserved.
 *
 * This software is dual-licensed to you under the MIT License (MIT) and 
 * the Universal Permissive License (UPL).  See the LICENSE file in the root
 * directory for license terms.  You may choose either license, or both.
 */

package com.oracle.iot.client.impl.trust;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Locale;

/**
 * This class provides methods to create X509 self-signed certificates.
 */
class SelfSignedX509CertificateFactory {
	/*
	 * the BER encoding of RSA Data Security, Inc.'s object identifier is 40 * 1
	 * + 2 = 42 = 2a. The encoding of 840 = 6 * 128 + 4816 is 86 48 and the
	 * encoding of 113549 = 6 * 1282 + 7716 * 128 + 16 is 86 f7 0d. This leads
	 * to the following BER encoding:
	 */

	/**
	 * DER-encoding of PKCS1-1 OID: {iso(1) member-body(2) us(840)
	 * rsadsi(113549) pkcs(1) pkcs-1(1)}
	 */
	private static final byte[] PKCS1_OID = { (byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xf7, (byte) 0x0d,
			(byte) 0x01, (byte) 0x01 };

	/**
	 * DER-encoding of COMMON NAME (CN) OID
	 */
	private static final byte[] COMMON_NAME_OID = { (byte) 0x55, (byte) 0x04, (byte) 0x03 };
	/**
	 * RSA ENCRYPTION (0x01).
	 */
	// private static final byte RSA_ENCRYPTION = 0x01;
	/**
	 * MD4_RSA algorithm (0x04).
	 */
	private static final byte MD5_RSA = 0x04;
	/**
	 * SHA1_RSA algorithm (0x05).
	 */
	private static final byte SHA1_RSA = 0x05;
	/**
	 * SHA256_RSA algorithm (0xb); sha256WithRSAEncryption(11)
	 */
	private static final byte SHA256_RSA = 0x0b;
	/**
	 * SHA256_RSA algorithm (0xc); sha384WithRSAEncryption(12)
	 */
	private static final byte SHA384_RSA = 0x0c;
	/**
	 * SHA256_RSA algorithm (0xd); sha512WithRSAEncryption(13)
	 */
	private static final byte SHA512_RSA = 0x0d;
	/**
	 * SHA256_RSA algorithm (0xe);sha224WithRSAEncryption(14)
	 */
	private static final byte SHA224_RSA = 0x0e;

	/**
	 * Random Number generator.
	 */
	private static Random random;

	private SelfSignedX509CertificateFactory() {
	}

	/**
	 * Generates an X509 Certificate wrapping an RSA public key.
	 * 
	 * @param privateKey
	 *            the RSA private key.
	 * @param publicKey
	 *            the RSA public key.
	 * @param algorithm
	 *            the signature algorithm.
	 * @param subject
	 *            the subject common name.
	 * @param notBeforeTime
	 *            the lower bound of the validity period.
	 * @param notAfterTime
	 *            the upper bound of the validity period.
	 * @return the self-signed certificate.
	 * @throws GeneralSecurityException
	 *             if any exception occurs while generating the certificate.
	 */
	static X509Certificate generateSelfSignedCertificate(RSAPrivateKey privateKey, RSAPublicKey publicKey, String algorithm,
			String subject, Date notBeforeTime, Date notAfterTime) throws GeneralSecurityException {
		try {
			byte[] encoded = generateCertificate(privateKey, publicKey, algorithm, subject, notBeforeTime, notAfterTime);

			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(encoded));
		} catch (IOException ioe) {
			throw new GeneralSecurityException(ioe);
		}
	}

	/**
	 * Generates the DER-encoded certificate.
	 * 
	 * <pre>
	 * 	   Certificate  ::=  SEQUENCE  {
	 *         tbsCertificate       TBSCertificate,
	 *         signatureAlgorithm   AlgorithmIdentifier,
	 *         signatureValue       BIT STRING  }
	 * 
	 * </pre>
	 */
	private static byte[] generateCertificate(RSAPrivateKey privateKey, RSAPublicKey publicKey, String algorithm, String subject,
			Date notBeforeTime, Date notAfterTime) throws IOException, GeneralSecurityException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		ByteArrayOutputStream substream = new ByteArrayOutputStream();

		byte[] tbsCertificate = generateTBSCertificate(publicKey, algorithm, subject, notBeforeTime, notAfterTime);
		substream.write(tbsCertificate);

		byte[] signatureAlgorithm = generateSignatureAlgorithm(algorithm);
		substream.write(signatureAlgorithm);

		byte[] signatureValue = generateSignatureValue(tbsCertificate, algorithm, privateKey);
		substream.write(signatureValue);

		DERUtil.writeDERAndReset(stream, DERUtil.SEQUENCE_TYPE, substream);

		return stream.toByteArray();
	}

	/**
	 * Generates the DER-encoded {@code tbsCertificate} field.
	 * 
	 * <pre>
	 *    tbsCertificate       TBSCertificate
	 * 
	 *    TBSCertificate  ::=  SEQUENCE  {
	 *         version         [0]  EXPLICIT Version DEFAULT v1,
	 *         serialNumber         CertificateSerialNumber,
	 *         signature            AlgorithmIdentifier,
	 *         issuer               Name,
	 *         validity             Validity,
	 *         subject              Name,
	 *         subjectPublicKeyInfo SubjectPublicKeyInfo,
	 *         issuerUniqueID  [1]  IMPLICIT UniqueIdentifier OPTIONAL,
	 *                              -- If present, version MUST be v2 or v3
	 *                              subjectUniqueID [2]  IMPLICIT UniqueIdentifier OPTIONAL,
	 *                              -- If present, version MUST be v2 or v3
	 *         extensions      [3]  EXPLICIT Extensions OPTIONAL
	 *                              -- If present, version MUST be v3
	 *         }
	 * </pre>
	 */
	private static byte[] generateTBSCertificate(RSAPublicKey publicKey, String algorithm, String subject, Date notBeforeTime,
			Date notAfterTime) throws IOException, NoSuchAlgorithmException {

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		ByteArrayOutputStream substream = new ByteArrayOutputStream();

		int version = 2;
		// version [0] EXPLICIT Version DEFAULT v1,
		// Version ::= INTEGER { v1(0), v2(1), v3(2) }
		{
			DERUtil.writeDERInteger(substream, version);
			DERUtil.writeDERAndReset(stream, DERUtil.EXPLICIT_TAG_TYPE, substream);
		}

		if (random == null) {
			random = new Random(publicKey.hashCode());
		}
                    
		int serialNumber = random.nextInt();
		// serialNumber CertificateSerialNumber,
		// CertificateSerialNumber ::= INTEGER
		{
			DERUtil.writeDERInteger(stream, serialNumber);
		}

		// signature AlgorithmIdentifier,
		byte[] signatureAlgorithm = generateSignatureAlgorithm(algorithm);
		stream.write(signatureAlgorithm);

		byte[] name = subject.getBytes("UTF-8");
		// issuer Name,
		{
			DERUtil.writeDER(substream, DERUtil.OID_TYPE, COMMON_NAME_OID);
			DERUtil.writeDER(substream, DERUtil.PRINTSTR_TYPE, name);
			DERUtil.writeDERAndReset(substream, DERUtil.SEQUENCE_TYPE, substream);
			DERUtil.writeDERAndReset(substream, DERUtil.SET_TYPE, substream);
			DERUtil.writeDERAndReset(stream, DERUtil.SEQUENCE_TYPE, substream);
		}

		// validity Validity,
		byte[] validity = generateValidity(notBeforeTime, notAfterTime);
		stream.write(validity);

		// subject Name,
		{
			DERUtil.writeDER(substream, DERUtil.OID_TYPE, COMMON_NAME_OID);
			DERUtil.writeDER(substream, DERUtil.PRINTSTR_TYPE, name);
			DERUtil.writeDERAndReset(substream, DERUtil.SEQUENCE_TYPE, substream);
			DERUtil.writeDERAndReset(substream, DERUtil.SET_TYPE, substream);
			DERUtil.writeDERAndReset(stream, DERUtil.SEQUENCE_TYPE, substream);
		}

		// subjectPublicKeyInfo SubjectPublicKeyInfo,
		// SubjectPublicKeyInfo ::= SEQUENCE {
		// algorithm AlgorithmIdentifier,
		// subjectPublicKey BIT STRING }
		byte[] subjectPublicKeyInfo = publicKey.getEncoded();
		stream.write(subjectPublicKeyInfo);

		DERUtil.writeDERAndReset(stream, DERUtil.SEQUENCE_TYPE, stream);

		return stream.toByteArray();
	}


	/**
	 * Generates the DER-encoded {@code signatureAlgorithm} field.
	 * 
	 * <pre>
	 *         signatureAlgorithm   AlgorithmIdentifier,
	 * </pre>
	 */
	private static byte[] generateSignatureAlgorithm(String algorithm) throws IOException, NoSuchAlgorithmException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		ByteArrayOutputStream substream = new ByteArrayOutputStream();

		byte signatureAlgorithm;
		if (algorithm.equalsIgnoreCase("MD5withRSA")) {
			signatureAlgorithm = MD5_RSA;
		} else if (algorithm.equalsIgnoreCase("SHA1withRSA")) {
			signatureAlgorithm = SHA1_RSA;
		} else if (algorithm.equalsIgnoreCase("SHA256withRSA")) {
			signatureAlgorithm = SHA256_RSA;
		} else if (algorithm.equalsIgnoreCase("SHA384withRSA")) {
			signatureAlgorithm = SHA384_RSA;
		} else if (algorithm.equalsIgnoreCase("SHA512withRSA")) {
			signatureAlgorithm = SHA512_RSA;
		} else if (algorithm.equalsIgnoreCase("SHA512withRSA")) {
			signatureAlgorithm = SHA224_RSA;
		} else {
			throw new NoSuchAlgorithmException();
		}
		// signature AlgorithmIdentifier,
		// AlgorithmIdentifier ::= SEQUENCE {
		// algorithm OBJECT IDENTIFIER,
		// parameters ANY DEFINED BY algorithm OPTIONAL }
		{
			DERUtil.writeDER(substream, DERUtil.OID_TYPE, PKCS1_OID, new byte[] { (byte) (0xFF & signatureAlgorithm) }, null);
			DERUtil.writeDER(substream, DERUtil.NULL, null);
			DERUtil.writeDERAndReset(stream, DERUtil.SEQUENCE_TYPE, substream);
		}

		return stream.toByteArray();
	}

	/**
	 * Generates the DER-encoded {@code signatureValue} field.
	 * 
	 * <pre>
	 *         signatureValue       BIT STRING
	 * </pre>
	 */
	private static byte[] generateSignatureValue(byte[] tbsCertificate, String algorithm, RSAPrivateKey privateKey)
			throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		byte[] signature;
		Signature sig = Signature.getInstance(algorithm);
		sig.initSign(privateKey);
		sig.update(tbsCertificate);
		signature = sig.sign();

		// signatureValue BIT STRING
		DERUtil.writeDERBitString(stream, signature, 0);

		return stream.toByteArray();
	}

	/**
	 * Generates the DER-encoded {@code validity} field.
	 * 
	 * <pre>
	 *    validity             Validity
	 *    
	 *    Validity ::= SEQUENCE {
	 *         notBefore      Time,
	 *         notAfter       Time }
	 * 
	 *    Time ::= CHOICE {
	 *         utcTime        UTCTime,
	 *         generalTime    GeneralizedTime }
	 * </pre>
	 */
	private static byte[] generateValidity(Date notBeforeTime, Date notAfterTime) throws IOException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		ByteArrayOutputStream substream = new ByteArrayOutputStream();

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddHHmmssZ", Locale.ROOT);

		byte[] nbTime = dateFormat.format(notBeforeTime).
                    getBytes("UTF-8");
		byte[] naTime = dateFormat.format(notAfterTime).
                    getBytes("UTF-8");

		DERUtil.writeDER(substream, DERUtil.UTC_TIME_TYPE, nbTime);
		DERUtil.writeDER(substream, DERUtil.UTC_TIME_TYPE, naTime);

		DERUtil.writeDERAndReset(stream, DERUtil.SEQUENCE_TYPE, substream);

		return stream.toByteArray();
	}

	/**
	 * Utility methods to deal with ASN1 DER Sequence constructs.
	 */
	private static class DERUtil {
		/**
		 * ASN INTEGER type used in certificate parsing (0x02).
		 */
		private static final byte INTEGER_TYPE = 0x02;
		/**
		 * ASN BIT STRING type used in certificate parsing (0x03).
		 */
		private static final byte BITSTRING_TYPE = 0x03;
		/**
		 * ASN NULL type (0x05).
		 */
		private static final byte NULL = 0x05;
		/**
		 * ASN OBJECT ID type used in certificate parsing (0x06).
		 */
		private static final byte OID_TYPE = 0x06;
		/**
		 * ASN PRINT STRING type used in certificate parsing (0x13).
		 */
		private static final byte PRINTSTR_TYPE = 0x13;
		/**
		 * UTCTime (0x17).
		 */
		private static final byte UTC_TIME_TYPE = 0x17;
		/**
		 * ASN SEQUENCE type used in certificate parsing (0x30).
		 */
		private static final byte SEQUENCE_TYPE = 0x30;
		/**
		 * ASN SET type used in certificate parsing (0x31).
		 */
		private static final byte SET_TYPE = 0x31;

		private static final byte EXPLICIT_TAG_TYPE = (byte) 0xa0;

		private DERUtil() {
		}

		private static void writeDERBitString(OutputStream stream, byte[] content, int unusedBits) throws IOException {
			stream.write(DERUtil.BITSTRING_TYPE);
			int length = (content != null ? content.length : 0) + 1;
			writeLengthSize(stream, length);
			stream.write(unusedBits);
			if (content != null)
				stream.write(content);
		}

		private static void writeDER(OutputStream stream, byte type, byte[] content) throws IOException {
			stream.write(type);
			int length = (content != null ? content.length : 0);
			writeLengthSize(stream, length);
			if (content != null)
				stream.write(content);
		}

		private static void writeDERAndReset(OutputStream stream, byte type, ByteArrayOutputStream content) throws IOException {
			byte[] data = content.toByteArray();
			content.reset();
			writeDER(stream, type, data);
		}

		private static void writeDER(OutputStream stream, byte type, byte[] content1, byte[] content2, byte[] content3)
				throws IOException {
			int length = (content1 != null ? content1.length : 0) + (content2 != null ? content2.length : 0)
					+ (content3 != null ? content3.length : 0);
			stream.write(type);
			writeLengthSize(stream, length);
			if (content1 != null)
				stream.write(content1);
			if (content2 != null)
				stream.write(content2);
			if (content3 != null)
				stream.write(content3);
		}

		private static void writeDERInteger(OutputStream stream, int i) throws IOException {
			stream.write(DERUtil.INTEGER_TYPE);
			int size = sizeOf(i);
			stream.write((byte) size); // Write the integer size
			{
				if (size == 4) {
					stream.write((byte) (i >> 24));
				}
				if (size >= 3) {
					stream.write((byte) (i >> 16));
				}
				if (size >= 2) {
					stream.write((byte) (i >> 8));
				}
				if (size >= 1) {
					stream.write((byte) (i));
				}
			}
		}

		private static void writeLengthSize(OutputStream stream, int length) throws IOException {
			if (length < 0x80) {
				// Definite short form
				stream.write((byte) length);
			} else {
				// Definite long form limited to 32bits
				int size = sizeOf(length);
				stream.write((byte) (0x80 | size)); // Write the length size
				{
					if (size == 4) {
						stream.write((byte) (length >> 24));
					}
					if (size >= 3) {
						stream.write((byte) (length >> 16));
					}
					if (size >= 2) {
						stream.write((byte) (length >> 8));
					}
					if (size >= 1) {
						stream.write((byte) (length));
					}
				}
			}
		}

		private static int sizeOf(int length) {
			if (((byte) (length >> 24)) != 0) {
				return 4;
			}
			if (((byte) (length >> 16)) != 0) {
				return 3;
			}
			if (((byte) (length >> 8)) != 0) {
				return 2;
			}
			return 1;
		}
	}
}
