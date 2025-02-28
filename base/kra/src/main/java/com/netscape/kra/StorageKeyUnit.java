// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.kra;

import java.io.CharConversionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.crypto.BadPaddingException;

import org.apache.commons.codec.binary.Base64;
import org.dogtagpki.server.kra.KRAEngine;
import org.dogtagpki.server.kra.KRAEngineConfig;
import org.mozilla.jss.CryptoManager;
import org.mozilla.jss.NotInitializedException;
import org.mozilla.jss.asn1.OBJECT_IDENTIFIER;
import org.mozilla.jss.crypto.Cipher;
import org.mozilla.jss.crypto.CryptoToken;
import org.mozilla.jss.crypto.EncryptionAlgorithm;
import org.mozilla.jss.crypto.IVParameterSpec;
import org.mozilla.jss.crypto.IllegalBlockSizeException;
import org.mozilla.jss.crypto.KeyGenerator;
import org.mozilla.jss.crypto.KeyWrapAlgorithm;
import org.mozilla.jss.crypto.KeyWrapper;
import org.mozilla.jss.crypto.ObjectNotFoundException;
import org.mozilla.jss.crypto.PBEAlgorithm;
import org.mozilla.jss.crypto.PBEKeyGenParams;
import org.mozilla.jss.crypto.PrivateKey;
import org.mozilla.jss.crypto.SymmetricKey;
import org.mozilla.jss.crypto.TokenCertificate;
import org.mozilla.jss.crypto.TokenException;
import org.mozilla.jss.crypto.X509Certificate;
import org.mozilla.jss.netscape.security.util.DerInputStream;
import org.mozilla.jss.netscape.security.util.DerOutputStream;
import org.mozilla.jss.netscape.security.util.DerValue;
import org.mozilla.jss.netscape.security.util.Utils;
import org.mozilla.jss.netscape.security.util.WrappingParams;
import org.mozilla.jss.util.Password;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.kra.EKRAException;
import com.netscape.certsrv.kra.IJoinShares;
import com.netscape.certsrv.kra.IShare;
import com.netscape.certsrv.security.Credential;
import com.netscape.certsrv.security.IStorageKeyUnit;
import com.netscape.cms.servlet.key.KeyRecordParser;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ConfigStore;
import com.netscape.cmscore.security.JssSubsystem;
import com.netscape.cmsutil.crypto.CryptoUtil;

/**
 * A class represents a storage key unit. Currently, this
 * is implemented with cryptix, the final implementation
 * should be built on JSS/HCL.
 *
 * @author thomask
 * @version $Revision$, $Date$
 */
public class StorageKeyUnit extends EncryptionUnit implements IStorageKeyUnit {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StorageKeyUnit.class);
    private ConfigStore mConfig;

    // private RSAPublicKey mPublicKey = null;
    // private RSAPrivateKey mPrivateKey = null;

    private ConfigStore mStorageConfig;
    private String mTokenFile = null;
    private X509Certificate mCert = null;
    private CryptoManager mManager = null;
    private CryptoToken mToken = null;
    private PrivateKey mPrivateKey = null;
    private byte mPrivateKeyData[] = null;
    private boolean mKeySplitting = false;
    private boolean useOAEPKeyWrap = false;
    private static final String PROP_N = "n";
    private static final String PROP_M = "m";
    private static final String PROP_UID = "uid";
    private static final String PROP_SHARE = "share";
    private static final String PROP_HARDWARE = "hardware";
    private static final String PROP_LOGOUT = "logout";
    public static final String PROP_NICKNAME = "nickName";
    public static final String PROP_KEYDB = "keydb";
    public static final String PROP_CERTDB = "certdb";
    public static final String PROP_MN = "mn";
    public static final String PROP_WRAPPING_CHOICE = "wrapping.choice";

    /**
     * Constructs this token.
     */
    public StorageKeyUnit() {
        super();
    }

    /**
     * Retrieves subsystem identifier.
     */
    public String getId() {
        return "storageKeyUnit";
    }

    /**
     * Sets subsystem identifier. Once the system is
     * loaded, system identifier cannot be changed
     * dynamically.
     */
    public void setId(String id) throws EBaseException {
        throw new EBaseException(CMS.getUserMessage("CMS_INVALID_OPERATION"));
    }

    @Override
    public WrappingParams getWrappingParams(boolean encrypt) throws Exception {
        String choice = null;
        try {
            choice = mConfig.getString(PROP_WRAPPING_CHOICE);
        } catch (EBaseException e) {
            // choice parameter does not exist
            // this is probably an old server
            // return the old params
            return this.getOldWrappingParams();
        }

        ConfigStore config = mConfig.getSubStore("wrapping." + choice, ConfigStore.class);
        if (config == null) {
            throw new EBaseException("Invalid config: Wrapping parameters not defined");
        }

        WrappingParams params = new WrappingParams();
        params.setSkType(config.getString(KeyRecordParser.OUT_SK_TYPE));
        params.setSkLength(config.getInteger(KeyRecordParser.OUT_SK_LENGTH, 0));
        String keyWrapAlg = config.getString(KeyRecordParser.OUT_SK_WRAP_ALGORITHM);
        if("RSA".equals(keyWrapAlg) && useOAEPKeyWrap == true) {
            keyWrapAlg = "RSAES-OAEP";
        }
        params.setSkWrapAlgorithm(keyWrapAlg);
        params.setSkKeyGenAlgorithm(config.getString(KeyRecordParser.OUT_SK_KEYGEN_ALGORITHM));
        params.setPayloadWrapAlgorithm(config.getString(KeyRecordParser.OUT_PL_WRAP_ALGORITHM));

        if (config.getString(KeyRecordParser.OUT_PL_ENCRYPTION_OID, null) != null) {
            String oidString = config.getString(KeyRecordParser.OUT_PL_ENCRYPTION_OID);
            params.setPayloadEncryptionAlgorithm(EncryptionAlgorithm.fromOID(new OBJECT_IDENTIFIER(oidString)));
        } else {
            params.setPayloadEncryptionAlgorithm(
                config.getString(KeyRecordParser.OUT_PL_ENCRYPTION_ALGORITHM),
                config.getString(KeyRecordParser.OUT_PL_ENCRYPTION_MODE),
                config.getString(KeyRecordParser.OUT_PL_ENCRYPTION_PADDING),
                config.getInteger(KeyRecordParser.OUT_SK_LENGTH));
        }

        byte [] iv = getConfigIV(
                config, KeyRecordParser.OUT_PL_ENCRYPTION_IV,
                KeyRecordParser.OUT_PL_ENCRYPTION_IV_LEN);
        if (iv != null) params.setPayloadEncryptionIV(new IVParameterSpec(iv));

        iv = getConfigIV(
                config, KeyRecordParser.OUT_PL_WRAP_IV,
                KeyRecordParser.OUT_PL_WRAP_IV_LEN);
        if (iv != null) params.setPayloadWrappingIV(new IVParameterSpec(iv));

        if (encrypt) {
            // Some HSMs have not yet implemented AES-KW.  Use AES-CBC-PAD instead
            if (params.getPayloadWrapAlgorithm().equals(KeyWrapAlgorithm.AES_KEY_WRAP) ||
                params.getPayloadWrapAlgorithm().equals(KeyWrapAlgorithm.AES_KEY_WRAP_PAD)) {
                params.setPayloadWrapAlgorithm(KeyWrapAlgorithm.AES_CBC_PAD);
                iv = CryptoUtil.getNonceData(16);
                params.setPayloadWrappingIV(new IVParameterSpec(iv));
            }
        }

        return params;
    }

    private byte[] getConfigIV(ConfigStore config, String iv_label, String len_label)
            throws Exception{
        String iv_string = config.getString(iv_label, null);
        String iv_len = config.getString(len_label, null);

        if (iv_string != null) {
            return Base64.decodeBase64(iv_string);
        }

        if (iv_len != null) {
            return CryptoUtil.getNonceData(Integer.parseInt(iv_len));
        }

        return null;
    }

    /**
     * return true if byte arrays are equal, false otherwise
     */
    private boolean byteArraysMatch(byte a[], byte b[]) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Initializes this subsystem.
     */
    public void init(ConfigStore config, boolean keySplitting)
            throws EBaseException {

        KRAEngine engine = KRAEngine.getInstance();
        mConfig = config;
        mKeySplitting = keySplitting;

        KRAEngineConfig kraCfg = null;
        kraCfg  = engine.getConfig();

        useOAEPKeyWrap = kraCfg.getBoolean("keyWrap.useOAEP",false);
        logger.debug("StorageKeyUnit.init: keyWrap.useOAEP" + useOAEPKeyWrap);
        try {
            mManager = CryptoManager.getInstance();
            mToken = getToken();
        } catch (NotInitializedException e) {
            logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_INIT", e.toString()), e);
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", e.toString()));
        }

        if (mConfig.getString(PROP_HARDWARE, null) != null) {
            System.setProperty("cms.skip_token", mConfig.getString(PROP_HARDWARE));

            // The strategy here is to read all the certs in the token
            // and cycle through them until we find one that matches the
            // kra-cert.db file

            if (mKeySplitting) {

                byte certFileData[] = null;
                FileInputStream fi = null;
                try {
                    File certFile = new File(
                            mConfig.getString(PROP_CERTDB));

                    certFileData = new byte[
                            (Long.valueOf(certFile.length())).intValue()];
                    fi = new FileInputStream(certFile);

                    fi.read(certFileData);
                    // pick up cert by nickName

                } catch (IOException e) {
                    logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_READ_CERT", e.toString()), e);
                    throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", e.toString()));

                } finally {
                    try {
                        if (fi != null)
                            fi.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    X509Certificate certs[] =
                            getToken().getCryptoStore().getCertificates();
                    for (int i = 0; i < certs.length; i++) {
                        if (byteArraysMatch(certs[i].getEncoded(), certFileData)) {
                            mCert = certs[i];
                        }
                    }

                    if (mCert == null) {
                        logger.error("Storage Cert could not be initialized. No cert in token matched kra-cert file");
                        throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", "mCert == null"));
                    } else {
                        logger.info("Using Storage Cert " + mCert.getSubjectDN());
                    }

                } catch (CertificateEncodingException e) {
                    logger.error("Error encoding cert: " + e.getMessage(), e);
                    throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", e.toString()));

                } catch (TokenException e) {
                    logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_READ_CERT", e.toString()), e);
                    throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", e.toString()));
                }
            }

        } else {

            // read certificate from file
            byte certData[] = null;
            FileInputStream fi = null;
            try {
                if (mKeySplitting) {
                    File certFile = new File(
                            mConfig.getString(PROP_CERTDB));

                    certData = new byte[
                            (Long.valueOf(certFile.length())).intValue()];
                    fi = new FileInputStream(certFile);

                    fi.read(certData);
                    // pick up cert by nickName
                    mCert = mManager.findCertByNickname(
                            config.getString(PROP_NICKNAME));

                } else {
                    mCert = mManager.findCertByNickname(
                            config.getString(PROP_NICKNAME));
                }

            } catch (IOException e) {
                logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_READ_CERT", e.toString()), e);
                throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", e.toString()), e);

            } catch (TokenException e) {
                logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_READ_CERT", e.toString()), e);
                throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", e.toString()), e);

            } catch (ObjectNotFoundException e) {
                logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_READ_CERT", e.toString()), e);
                // XXX - this import wont work
                try {
                    mCert = mManager.importCertPackage(certData,
                                "kraStorageCert");
                } catch (Exception ex) {
                    logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_IMPORT_CERT", e.toString()), ex);
                    throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", ex.toString()));
                }

            } finally {
                if (fi != null) {
                    try {
                        fi.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (mKeySplitting) {
                // read private key from the file
                try {
                    File priFile = new File(mConfig.getString(PROP_KEYDB));

                    mPrivateKeyData = new byte[
                            (Long.valueOf(priFile.length())).intValue()];
                    fi = new FileInputStream(priFile);

                    fi.read(mPrivateKeyData);

                } catch (IOException e) {
                    logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_READ_PRIVATE", e.toString()), e);
                    throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1", e.toString()));

                } finally {
                    if (fi != null) {
                        try {
                            fi.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }

        if (mKeySplitting) {
            // open internal data storage configuration
            mTokenFile = mConfig.getString(PROP_MN);
            try {
                // read m, n and no of identifier
                mStorageConfig = engine.loadConfigStore(mTokenFile);
            } catch (EBaseException e) {
                logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_READ_MN", e.toString()), e);
                throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_OPERATION"), e);
            }
        }

        try {
            if (mCert == null) {
                logger.debug("mCert is null...retrieving " + config.getString(PROP_NICKNAME));
                mCert = mManager.findCertByNickname(
                           config.getString(PROP_NICKNAME));
                logger.debug("mCert = " + mCert);
            }
        } catch (Exception e) {
            logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_READ_CERT", e.toString()), e);
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", e.toString()), e);
        }

    }

    /**
     * Starts up this subsystem.
     */
    public void startup() throws EBaseException {
    }

    /**
     * Shutdowns this subsystem.
     */
    public void shutdown() {
    }

    /**
     * Returns the configuration store of this token.
     */
    public ConfigStore getConfigStore() {
        return mConfig;
    }

    public static SymmetricKey buildSymmetricKeyWithInternalStorage(
            String pin) throws EBaseException {
        try {
            return buildSymmetricKey(CryptoManager.getInstance().getInternalKeyStorageToken(), pin);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds symmetric key from the given password.
     */
    public static SymmetricKey buildSymmetricKey(CryptoToken token,
            String pin) throws EBaseException {
        Password pass = new Password(pin.toCharArray());
        try {
            KeyGenerator kg = token.getKeyGenerator(
                        PBEAlgorithm.PBE_SHA1_DES3_CBC);
            byte salt[] = { 0x01, 0x01, 0x01, 0x01,
                    0x01, 0x01, 0x01, 0x01 };
            PBEKeyGenParams kgp = new PBEKeyGenParams(pass,
                    salt, 5);

            kg.initialize(kgp);
            return kg.generate();
        } catch (TokenException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "buildSymmetricKey:" +
                                e.toString()));
        } catch (NoSuchAlgorithmException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "buildSymmetricKey:" +
                                e.toString()));
        } catch (InvalidAlgorithmParameterException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "buildSymmetricKey:" +
                                e.toString()));
        } catch (CharConversionException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "buildSymmetricKey:" +
                                e.toString()));
        } finally {
            pass.clear();
        }
    }

    /**
     * Unwraps the storage key with the given symmetric key.
     */
    public PrivateKey unwrapStorageKey(CryptoToken token,
            SymmetricKey sk, byte wrapped[],
            PublicKey pubKey)
            throws EBaseException {
        try {

            logger.debug("StorageKeyUnit.unwrapStorageKey.");

            KeyWrapper wrapper = token.getKeyWrapper(
                    KeyWrapAlgorithm.DES3_CBC_PAD);

            wrapper.initUnwrap(sk, IV);

            // XXX - it does not like the public key that is
            // not a crypto X509Certificate
            PrivateKey pk = wrapper.unwrapTemporaryPrivate(wrapped,
                    PrivateKey.RSA, pubKey);

            return pk;
        } catch (TokenException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "unwrapStorageKey:" +
                                e.toString()));
        } catch (NoSuchAlgorithmException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "unwrapStorageKey:" +
                                e.toString()));
        } catch (InvalidKeyException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "unwrapStorageKey:" +
                                e.toString()));
        } catch (InvalidAlgorithmParameterException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "unwrapStorageKey:" +
                                e.toString()));
        }
    }

    /**
     * Used by config-cert.
     */
    public byte[] wrapStorageKey(CryptoToken token,
            SymmetricKey sk, PrivateKey pri)
            throws EBaseException {
        logger.debug("StorageKeyUnit.wrapStorageKey.");
        try {
            // move public & private to config/storage.dat
            // delete private key
            return CryptoUtil.wrapUsingSymmetricKey(
                    token,
                    sk,
                    pri,
                    IV,
                    KeyWrapAlgorithm.DES3_CBC_PAD);
        } catch (Exception e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        "wrapStorageKey:" +
                                e.toString()), e);
        }
    }

    /**
     * Logins to this token.
     */
    @Override
    public void login(String pin) throws EBaseException {
        if (mConfig.getString(PROP_HARDWARE, null) != null) {
            Password password = new Password(pin.toCharArray());
            try {
                getToken().login(password);
                PrivateKey pk[] = getToken().getCryptoStore().getPrivateKeys();

                for (int i = 0; i < pk.length; i++) {
                    if (arraysEqual(pk[i].getUniqueID(),
                            ((TokenCertificate) mCert).getUniqueID())) {
                        mPrivateKey = pk[i];
                    }
                }
            } catch (Exception e) {
                logger.warn(CMS.getLogMessage("CMSCORE_KRA_STORAGE_LOGIN", e.toString()), e);
            } finally {
                password.clear();
            }

        } else {
            try {
                SymmetricKey sk = buildSymmetricKey(mToken, pin);

                mPrivateKey = unwrapStorageKey(mToken, sk,
                            mPrivateKeyData, getPublicKey());
            } catch (Exception e) {
                logger.warn(CMS.getLogMessage("CMSCORE_KRA_STORAGE_LOGIN", e.toString()), e);
            }
            if (mPrivateKey == null) {
                mPrivateKey = getPrivateKey();
            }
        }
    }

    /**
     * Logins to this token.
     */
    @Override
    public void login(Credential creds[])
            throws EBaseException {
        String pwd = constructPassword(creds);

        login(pwd);
    }

    /**
     * Logout from this token.
     */
    @Override
    public void logout() {
        try {
            if (mConfig.getString(PROP_HARDWARE, null) != null) {
                if (mConfig.getBoolean(PROP_LOGOUT, false)) {
                    getToken().logout();
                }
            }
        } catch (Exception e) {
            logger.warn(CMS.getLogMessage("CMSCORE_KRA_STORAGE_LOGOUT", e.toString()), e);

        }
        mPrivateKey = null;
    }

    /**
     * Returns a list of recovery agent identifiers.
     */
    @Override
    public Enumeration<String> getAgentIdentifiers() {
        Vector<String> v = new Vector<>();

        for (int i = 0;; i++) {
            try {
                String uid =
                        mStorageConfig.getString(PROP_UID + i);

                if (uid == null)
                    break;
                v.addElement(uid);
            } catch (EBaseException e) {
                break;
            }
        }
        return v.elements();
    }

    /**
     * Changes agent password.
     */
    @Override
    public boolean changeAgentPassword(String id, String oldpwd,
            String newpwd) throws EBaseException {

        KRAEngine engine = KRAEngine.getInstance();
        JssSubsystem jssSubsystem = engine.getJSSSubsystem();

        // locate the id(s)
        byte share[]=null;
        for (int i = 0;; i++) {
            try {
                String uid =
                        mStorageConfig.getString(PROP_UID + i);

                if (uid == null)
                    break;
                if (id.equals(uid)) {
                    share = decryptShareWithInternalStorage(mStorageConfig.getString(PROP_SHARE + i), oldpwd);

                    mStorageConfig.putString(PROP_SHARE + i,
                            encryptShareWithInternalStorage(
                                    share, newpwd));
                    mStorageConfig.commit(false);
                    jssSubsystem.obscureBytes(share);
                    return true;
                }
            } catch (Exception e) {
                jssSubsystem.obscureBytes(share);
                break;
            }
        }
        return false;
    }

    /**
     * Changes the m out of n recovery schema.
     */
    @Override
    public boolean changeAgentMN(int new_n, int new_m,
            Credential oldcreds[],
            Credential newcreds[])
            throws EBaseException {

        if (new_n != newcreds.length) {
            throw new EKRAException(CMS.getUserMessage("CMS_KRA_INVALID_N"));
        }

        // XXX - verify and construct original password
        String secret = constructPassword(oldcreds);

        // XXX - remove extra configuration
        for (int j = new_n; j < getNoOfAgents(); j++) {
            mStorageConfig.remove(PROP_UID + j);
            mStorageConfig.remove(PROP_SHARE + j);
        }

        // XXX - split pwd into n pieces
        byte shares[][] = new byte[newcreds.length][];

        IShare s = null;
        try {
            String className = mConfig.getString("share_class",
                                       "com.netscape.cms.shares.OldShare");
            s = (IShare) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.warn("Loading Shares error " + e.getMessage(), e);
        }
        if (s == null) {
            logger.warn("Share plugin is not found");
            return false;
        }

        try {
            s.initialize(secret.getBytes(), new_m);
        } catch (Exception e) {
            logger.warn("Failed to initialize Share plugin: " + e.getMessage(), e);
            return false;
        }

        for (int i = 0; i < newcreds.length; i++) {
            byte share[] = s.createShare(i + 1);

            shares[i] = share;
        }

        // store the new shares into configuration
        mStorageConfig.putInteger(PROP_N, new_n);
        mStorageConfig.putInteger(PROP_M, new_m);
        for (int i = 0; i < newcreds.length; i++) {
            mStorageConfig.putString(PROP_UID + i,
                    newcreds[i].getIdentifier());
            // use password to encrypt shares...
            mStorageConfig.putString(PROP_SHARE + i,
                    encryptShareWithInternalStorage(shares[i],
                            newcreds[i].getPassword()));
        }

        try {
            mStorageConfig.commit(false);
            return true;
        } catch (EBaseException e) {
            logger.warn(CMS.getLogMessage("CMSCORE_KRA_STORAGE_CHANGE_MN", e.toString()), e);
        }
        return false;
    }

    /**
     * Returns number of recovery agents.
     */
    @Override
    public int getNoOfAgents() throws EBaseException {
        return mStorageConfig.getInteger(PROP_N);
    }

    /**
     * Returns number of recovery agents required for
     * recovery operation.
     */
    @Override
    public int getNoOfRequiredAgents() throws EBaseException {
        return mStorageConfig.getInteger(PROP_M);
    }

    @Override
    public void setNoOfRequiredAgents(int number) {
        mStorageConfig.putInteger(PROP_M, number);
    }

    @Override
    public CryptoToken getInternalToken() {
        try {
            return CryptoManager.getInstance().getInternalKeyStorageToken();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public CryptoToken getToken() {
        try {
            String tokenName = mConfig.getString(PROP_HARDWARE, null);
            return CryptoUtil.getKeyStorageToken(tokenName);

        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public CryptoToken getToken(org.mozilla.jss.crypto.X509Certificate cert) {
        return getToken();
    }

    /**
     * Returns the certificate blob.
     */
    @Override
    public PublicKey getPublicKey() {
        // NEED to move this key into internal storage token.
        return mCert.getPublicKey();
    }

    @Override
    public PrivateKey getPrivateKey() {

        if (!mKeySplitting) {
            try {
                PrivateKey pk[] = getToken().getCryptoStore().getPrivateKeys();
                for (int i = 0; i < pk.length; i++) {
                    if (arraysEqual(pk[i].getUniqueID(),
                            ((TokenCertificate) mCert).getUniqueID())) {
                        return pk[i];
                    }
                }
            } catch (TokenException e) {
            }
            return null;
        } else {
            return mPrivateKey;
        }
    }

    @Override
    public PrivateKey getPrivateKey(org.mozilla.jss.crypto.X509Certificate cert) {
        return getPrivateKey();
    }

    /**
     * Verifies the integrity of the given key pairs.
     */
    public void verify(byte publicKey[], PrivateKey privateKey)
            throws EBaseException {
        // XXX
    }

    public String encryptShareWithInternalStorage(
            byte share[], String pwd)
            throws EBaseException {
        try {
            return encryptShare(CryptoManager.getInstance().getInternalKeyStorageToken(), share, pwd);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Protectes the share with the given password.
     */
    public String encryptShare(CryptoToken token,
            byte share[], String pwd)
            throws EBaseException {
        try {
            logger.debug("StorageKeyUnit.encryptShare");
            Cipher cipher = token.getCipherContext(
                    EncryptionAlgorithm.DES3_CBC_PAD);
            SymmetricKey sk = StorageKeyUnit.buildSymmetricKey(token, pwd);

            cipher.initEncrypt(sk, IV);
            byte prev[] = preVerify(share);
            byte enc[] = cipher.doFinal(prev);

            return Utils.base64encode(enc, true).trim();
        } catch (NoSuchAlgorithmException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        e.toString()));
        } catch (TokenException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        e.toString()));
        } catch (InvalidKeyException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        e.toString()));
        } catch (InvalidAlgorithmParameterException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        e.toString()));
        } catch (BadPaddingException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        e.toString()));
        } catch (IllegalBlockSizeException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_INVALID_KEY_1",
                        e.toString()));
        }
    }

    public static byte[] preVerify(byte share[]) {
        byte data[] = new byte[share.length + 2];

        data[0] = 0;
        data[1] = 0;
        for (int i = 0; i < share.length; i++) {
            data[2 + i] = share[i];
        }
        return data;
    }

    public static boolean verifyShare(byte share[]) {
        if (share[0] == 0 && share[1] == 0) {
            return true;
        } else {
            return false;
        }
    }

    public static byte[] postVerify(byte share[]) {
        byte data[] = new byte[share.length - 2];

        for (int i = 2; i < share.length; i++) {
            data[i - 2] = share[i];
        }
        return data;
    }

    public void checkPassword(String userid, String pwd) throws EBaseException {

        KRAEngine engine = KRAEngine.getInstance();
        JssSubsystem jssSubsystem = engine.getJSSSubsystem();

        for (int i = 0;; i++) {
            String uid = null;

            try {
                uid = mStorageConfig.getString(PROP_UID + i);
                if (uid == null)
                    break;
            } catch (Exception e) {
                break;
            }
            if (uid.equals(userid)) {
                byte data[] = decryptShareWithInternalStorage(
                        mStorageConfig.getString(PROP_SHARE + i),
                        pwd);
                if (data == null) {
                    throw new EBaseException(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
                } else {
                    jssSubsystem.obscureBytes(data);
                }
                return;
            }
        }
        throw new EBaseException(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));

    }

    public byte[] decryptShareWithInternalStorage(
            String encoding, String pwd)
            throws EBaseException {
        try {
            return decryptShare(CryptoManager.getInstance().getInternalKeyStorageToken(), encoding, pwd);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decrypts shares with the given password.
     */
    public byte[] decryptShare(CryptoToken token,
            String encoding, String pwd)
            throws EBaseException {
        try {
            logger.debug("StorageKeyUnit.decryptShare");
            byte share[] = Utils.base64decode(encoding);
            Cipher cipher = token.getCipherContext(
                    EncryptionAlgorithm.DES3_CBC_PAD);
            SymmetricKey sk = StorageKeyUnit.buildSymmetricKey(
                    token, pwd);

            cipher.initDecrypt(sk, IV);
            byte dec[] = cipher.doFinal(share);

            if (dec == null || !verifyShare(dec)) {
                // invalid passwod
                throw new EBaseException(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
            }
            return postVerify(dec);
        } catch (OutOfMemoryError e) {
            // XXX - this happens in cipher.doFinal when
            // the given share is not valid (the password
            // given from the agent is not correct).
            // Actulla, cipher.doFinal should return
            // something better than this!
            //
            // e.printStackTrace();
            //
            throw new EBaseException(CMS.getUserMessage("CMS_KRA_INVALID_PASSWORD",
                        e.toString()));
        } catch (TokenException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_KRA_INVALID_PASSWORD",
                        e.toString()));
        } catch (NoSuchAlgorithmException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_KRA_INVALID_PASSWORD",
                        e.toString()));
        } catch (InvalidKeyException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_KRA_INVALID_PASSWORD",
                        e.toString()));
        } catch (InvalidAlgorithmParameterException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_KRA_INVALID_PASSWORD",
                        e.toString()));
        } catch (IllegalBlockSizeException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_KRA_INVALID_PASSWORD",
                        e.toString()));
        } catch (BadPaddingException e) {
            throw new EBaseException(CMS.getUserMessage("CMS_KRA_INVALID_PASSWORD",
                        e.toString()));
        }
    }

    /**
     * Reconstructs password from recovery agents.
     */
    private String constructPassword(Credential creds[])
            throws EBaseException {

        KRAEngine engine = KRAEngine.getInstance();
        JssSubsystem jssSubsystem = engine.getJSSSubsystem();

        // sort the credential according to the order in
        // configuration file
        Hashtable<String, byte[]> v = new Hashtable<>();

        for (int i = 0;; i++) {
            String uid = null;

            try {
                uid = mStorageConfig.getString(PROP_UID + i);
                if (uid == null)
                    break;
            } catch (Exception e) {
                break;
            }
            for (int j = 0; j < creds.length; j++) {
                if (uid.equals(creds[j].getIdentifier())) {
                    byte pwd[] = decryptShareWithInternalStorage(
                            mStorageConfig.getString(
                                    PROP_SHARE + i),
                            creds[j].getPassword());
                    if (pwd == null) {
                        throw new EBaseException(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
                    }

                    v.put(Integer.toString(i), pwd);
                    jssSubsystem.obscureBytes(pwd);
                    break;
                }
            }
        }

        if (v.size() < 0) {
            throw new EBaseException(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
        }

        if (v.size() != creds.length) {
            throw new EBaseException(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
        }

        IJoinShares j = null;
        try {
            String className = mConfig.getString("joinshares_class",
                                       "com.netscape.cms.shares.OldJoinShares");
            j = (IJoinShares) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.warn("JoinShares error " + e.getMessage(), e);
        }
        if (j == null) {
            logger.error("JoinShares plugin is not found");
            throw new EBaseException(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
        }

        try {
            j.initialize(v.size());
        } catch (Exception e) {
            logger.error("Failed to initialize JoinShares: " + e.getMessage(), e);
            throw new EBaseException(CMS.getUserMessage("CMS_AUTHENTICATION_INVALID_CREDENTIAL"));
        }
        Enumeration<String> e = v.keys();

        while (e.hasMoreElements()) {
            String next = e.nextElement();

            j.addShare(Integer.parseInt(next) + 1, v.get(next));
        }
        try {
            byte secret[] = j.recoverSecret();
            String pwd = new String(secret);

            jssSubsystem.obscureBytes(secret);

            return pwd;
        } catch (Exception ee) {
            logger.error(CMS.getLogMessage("CMSCORE_KRA_STORAGE_RECONSTRUCT", e.toString()), ee);
            throw new EBaseException(CMS.getUserMessage("CMS_KRA_INVALID_PASSWORD",
                        ee.toString()), ee);
        }
    }

    public static boolean arraysEqual(byte[] bytes, byte[] ints) {
        if (bytes == null || ints == null) {
            return false;
        }

        if (bytes.length != ints.length) {
            return false;
        }

        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != ints[i]) {
                return false;
            }
        }
        return true;
    }

    /****************************************************************************************
     * Methods to encrypt and store secrets in the database
     ***************************************************************************************/

    @Override
    public byte[] encryptInternalPrivate(byte priKey[], WrappingParams params) throws Exception {
        try (DerOutputStream out = new DerOutputStream()) {
            logger.debug("StorageKeyUnit.encryptInternalPrivate");
            CryptoToken internalToken = getInternalToken();

            // (1) generate session key
            SymmetricKey sk = CryptoUtil.generateKey(
                    internalToken,
                    params.getSkKeyGenAlgorithm(),
                    params.getSkLength(),
                    null,
                    false);

            // (2) wrap private key with session key
            byte[] pri = CryptoUtil.encryptUsingSymmetricKey(
                    internalToken,
                    sk,
                    priKey,
                    params.getPayloadEncryptionAlgorithm(),
                    params.getPayloadEncryptionIV());

            // (3) wrap session with storage public
            byte[] session = CryptoUtil.wrapUsingPublicKey(
                    internalToken,
                    getPublicKey(),
                    sk,
                    params.getSkWrapAlgorithm());

            // use MY own structure for now:
            // SEQUENCE {
            //     encryptedSession OCTET STRING,
            //     encryptedPrivate OCTET STRING
            // }

            DerOutputStream tmp = new DerOutputStream();

            tmp.putOctetString(session);
            tmp.putOctetString(pri);
            out.write(DerValue.tag_Sequence, tmp);

            return out.toByteArray();
        }
    }

    @Override
    public byte[] wrap(PrivateKey privKey, WrappingParams params) throws Exception {
        return _wrap(privKey,null, params);
    }

    @Override
    public byte[] wrap(SymmetricKey symmKey, WrappingParams params) throws Exception {
        return _wrap(null,symmKey, params);
    }

    /***
     * Internal wrap, accounts for either private or symmetric key
     * @param params TODO
     */
    private byte[] _wrap(PrivateKey priKey, SymmetricKey symmKey, WrappingParams params) throws Exception {
        try (DerOutputStream out = new DerOutputStream()) {
            if ((priKey == null && symmKey == null) || (priKey != null && symmKey != null)) {
                return null;
            }
            logger.debug("StorageKeyUnit.wrap interal.");
            CryptoToken token = getToken();

            SymmetricKey.Usage usages[] = new SymmetricKey.Usage[2];
            usages[0] = SymmetricKey.Usage.WRAP;
            usages[1] = SymmetricKey.Usage.UNWRAP;

            // (1) generate session key
            SymmetricKey sk = CryptoUtil.generateKey(
                    token,
                    params.getSkKeyGenAlgorithm(),
                    params.getSkLength(),
                    usages,
                    true);

            // (2) wrap private key with session key
            // KeyWrapper wrapper = internalToken.getKeyWrapper(

            byte pri[] = null;

            if (priKey != null) {
                pri = CryptoUtil.wrapUsingSymmetricKey(
                        token,
                        sk,
                        priKey,
                        params.getPayloadWrappingIV(),
                        params.getPayloadWrapAlgorithm());
            } else if (symmKey != null) {
                pri = CryptoUtil.wrapUsingSymmetricKey(
                        token,
                        sk,
                        symmKey,
                        params.getPayloadWrappingIV(),
                        params.getPayloadWrapAlgorithm());
            }

            logger.debug("StorageKeyUnit:wrap() privKey wrapped");

            byte[] session = CryptoUtil.wrapUsingPublicKey(
                    token,
                    getPublicKey(),
                    sk,
                    params.getSkWrapAlgorithm());
            logger.debug("StorageKeyUnit:wrap() session key wrapped");

            // use MY own structure for now:
            // SEQUENCE {
            //     encryptedSession OCTET STRING,
            //     encryptedPrivate OCTET STRING
            // }

            DerOutputStream tmp = new DerOutputStream();

            tmp.putOctetString(session);
            tmp.putOctetString(pri);
            out.write(DerValue.tag_Sequence, tmp);

            return out.toByteArray();
        }
    }

    /****************************************************************************************
     * Methods to decrypt and retrieve secrets from the database
     ***************************************************************************************/

    @Override
    public byte[] decryptInternalPrivate(byte wrappedKeyData[], WrappingParams params)
            throws Exception {
        logger.debug("StorageKeyUnit.decryptInternalPrivate");
        DerValue val = new DerValue(wrappedKeyData);
        // val.tag == DerValue.tag_Sequence
        DerInputStream in = val.data;
        DerValue dSession = in.getDerValue();
        byte session[] = dSession.getOctetString();
        DerValue dPri = in.getDerValue();
        byte pri[] = dPri.getOctetString();

        CryptoToken token = getToken();

        // (1) unwrap the session key
        logger.debug("decryptInternalPrivate(): getting key wrapper on slot:" + token.getName());
        SymmetricKey sk = unwrap_session_key(token, session, SymmetricKey.Usage.DECRYPT, params);

        // (2) decrypt the private key
        return CryptoUtil.decryptUsingSymmetricKey(
                token,
                params.getPayloadEncryptionIV(),
                pri,
                sk,
                params.getPayloadEncryptionAlgorithm());
    }

    @Override
    public SymmetricKey unwrap(byte wrappedKeyData[], SymmetricKey.Type algorithm, int keySize,
            WrappingParams params) throws Exception {
        DerValue val = new DerValue(wrappedKeyData);
        // val.tag == DerValue.tag_Sequence
        DerInputStream in = val.data;
        DerValue dSession = in.getDerValue();
        byte session[] = dSession.getOctetString();
        DerValue dPri = in.getDerValue();
        byte pri[] = dPri.getOctetString();

        CryptoToken token = getToken();
        // (1) unwrap the session key
        SymmetricKey sk = unwrap_session_key(token, session, SymmetricKey.Usage.UNWRAP, params);

        // (2) unwrap the session-wrapped-symmetric key
        return CryptoUtil.unwrap(
                token,
                algorithm,
                keySize,
                SymmetricKey.Usage.UNWRAP,
                sk,
                pri,
                params.getPayloadWrapAlgorithm(),
                params.getPayloadWrappingIV());
    }

    @Override
    public PrivateKey unwrap(byte wrappedKeyData[], PublicKey pubKey, boolean temporary, WrappingParams params)
            throws Exception {
        DerValue val = new DerValue(wrappedKeyData);
        // val.tag == DerValue.tag_Sequence
        DerInputStream in = val.data;
        DerValue dSession = in.getDerValue();
        byte session[] = dSession.getOctetString();
        DerValue dPri = in.getDerValue();
        byte pri[] = dPri.getOctetString();

        CryptoToken token = getToken();
        // (1) unwrap the session key
        SymmetricKey sk = unwrap_session_key(token, session, SymmetricKey.Usage.UNWRAP, params);

        // (2) unwrap the private key
        return CryptoUtil.unwrap(
                token,
                pubKey,
                temporary,
                sk,
                pri,
                params.getPayloadWrapAlgorithm(),
                params.getPayloadWrappingIV());
    }
}
