/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.security.tests.storage;

import java.io.IOException;
import java.security.Provider;
import java.security.Security;
import java.util.*;
import junit.framework.*;
import org.eclipse.equinox.security.storage.*;
import org.eclipse.equinox.security.storage.provider.IProviderHints;

/**
 * This is a semi-manual test; expected to be run with modules requiring
 * UI interactions. Set configuration before executing this so that desired
 * storage module is selected.
 */
public class ManualTest extends TestCase {

	static private final String JAVA_MODULE_ID = "org.eclipse.equinox.security.javaCrypt"; //$NON-NLS-1$

	public ManualTest() {
		super();
	}

	public ManualTest(String name) {
		super(name);
	}

	final private String passwordSample = "uYTIU689_~@@/";
	final private String loginSample = "cheburashka";

	public void testBasic() {

		// Note that this skips Alg.Alias.Cipher.ABC
		// few aliases are useful and it will be harder to separate human-redable
		// aliases from internal ones
		Provider[] providers = Security.getProviders();
		for (int i = 0; i < providers.length; i++) {
			for (Iterator j = providers[i].entrySet().iterator(); j.hasNext();) {
				Map.Entry entry = (Map.Entry) j.next();
				String key = (String) entry.getKey();
				if (key == null)
					continue;
				if (key.indexOf(' ') != -1) // skips "[Cipher.ABC SupportedPaddings]", etc.
					continue;
				if (key.startsWith("Cipher.")) {
					String cipher = key.substring(7); // length("Cipher.")
					System.out.println("[cypher] " + cipher);
				}
				if (key.startsWith("SecretKeyFactory.")) {
					String keyFactory = key.substring(17); // length("SecretKeyFactory.")
					System.out.println("[keyFactory] " + keyFactory);
				}
			}

			/*
			 * In theory, all those ciphers support "Raw" key factory format - the SecretKeyFactory
			 * algorithm should be really irrelevant. That said, Blowfish would not work with JavaCrypt
			 * - so need to manually test all and exclude those that we know not supported
			 * (or provide a roundtrip test - if it fails, exclude the value)
			[keyFactory] PBKDF2WithHmacSHA1
			[keyFactory] DES
			[keyFactory] DESede
			[keyFactory] PBE
			[keyFactory] PBEWithSHA1AndRC2_40
			[keyFactory] PBEWithSHA1AndDESede
			[keyFactory] PBEWithMD5AndTripleDES
			[keyFactory] PBEWithMD5AndDES


			[cypher] DES
			[cypher] Blowfish
			[cypher] RC2
			[cypher] AES
			[cypher] DESede
			[cypher] PBEWithMD5AndDES
			[cypher] PBEWithMD5AndTripleDES
			[cypher] ARCFOUR
			[cypher] RSA
			[cypher] AESWrap
			[cypher] PBEWithSHA1AndRC2_40
			[cypher] PBEWithSHA1AndDESede
			[cypher] DESedeWrap

			http://java.sun.com/javase/6/docs/technotes/guides/security/StandardNames.html#SecretKeyFactory
			http://java.sun.com/j2se/1.4.2/docs/guide/security/jce/JCERefGuide.html#AppA
			 */
		}
	}

	/**
	 * Manual test for Java module to see password prompt functionality
	 */
	public void testJavaModule() throws IOException, StorageException {
		// manual test for the Java module
		Map options = new HashMap(1);
		options.put(IProviderHints.REQUIRED_MODULE_ID, JAVA_MODULE_ID);
		ISecurePreferences storage = SecurePreferencesFactory.open(null, options);
		ISecurePreferences node = storage.node("/cvs/account1");
		node.put("login", loginSample, true);
		node.put("password", passwordSample, true);
		assertEquals(loginSample, node.get("login", null));
		assertEquals(passwordSample, node.get("password", null));
	}

	public static Test suite() {
		return new TestSuite(ManualTest.class);
	}

}
