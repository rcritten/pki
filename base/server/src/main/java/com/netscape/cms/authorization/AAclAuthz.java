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
package com.netscape.cms.authorization;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import org.dogtagpki.server.authentication.AuthToken;
import org.dogtagpki.server.authorization.AuthorizationConfig;
import org.dogtagpki.server.authorization.AuthzManagerConfig;
import org.dogtagpki.server.authorization.AuthzToken;
import org.dogtagpki.server.authorization.IAuthzManager;
import org.mozilla.jss.netscape.security.util.Utils;

import com.netscape.certsrv.acls.ACLEntry;
import com.netscape.certsrv.acls.EACLsException;
import com.netscape.certsrv.authorization.EAuthzAccessDenied;
import com.netscape.certsrv.authorization.EAuthzInternalError;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.evaluators.IAccessEvaluator;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.CMSEngine;
import com.netscape.cmscore.apps.EngineConfig;
import com.netscape.cmscore.base.ConfigStore;

/**
 * An abstract class represents an authorization manager that governs the
 * access of internal resources such as servlets.
 * It parses in the ACLs associated with each protected
 * resources, and provides protected method <CODE>checkPermission</CODE> for code that needs to verify access before
 * performing
 * actions.
 * <P>
 * Here is a sample resourceACLS for a resource
 *
 * <PRE>
 *   certServer.UsrGrpAdminServlet:
 *       execute:
 *           deny (execute) user="tempAdmin";
 *           allow (execute) group="Administrators";
 * </PRE>
 *
 * To perform permission checking, code call authz mgr authorize() method to verify access. See AuthzMgr for calling
 * example.
 * <P>
 * default "evaluators" are used to evaluate the "group=.." or "user=.." rules. See evaluator for more info
 *
 * @version $Revision$, $Date$
 * @see <A HREF="http://developer.netscape.com/library/documentation/enterprise/admnunix/aclfiles.htm">ACL Files</A>
 */
public abstract class AAclAuthz implements IAuthzManager {

    public static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AAclAuthz.class);

    public enum EvaluationOrder { DENY_ALLOW , ALLOW_DENY }

    protected static final String PROP_CLASS = "class";
    protected static final String PROP_IMPL = "impl";
    protected static final String PROP_EVAL = "accessEvaluator";

    protected static final String ACLS_ATTR = "aclResources";

    /* name of this authorization manager instance */
    private String mName = null;

    /* name of the authorization manager plugin */
    private String mImplName = null;

    private AuthzManagerConfig mConfig;

    private Hashtable<String, ACL> mACLs = new Hashtable<>();
    private Hashtable<String, IAccessEvaluator> mEvaluators = new Hashtable<>();

    /* Vector of extendedPluginInfo strings */
    protected static Vector<String> mExtendedPluginInfo = null;

    protected static String[] mConfigParams = null;

    static {
        mExtendedPluginInfo = new Vector<>();
    }

    /**
     * Constructor
     */
    protected AAclAuthz() {
    }

    /**
     * Initializes
     */
    @Override
    public void init(String name, String implName, AuthzManagerConfig config)
            throws EBaseException {
        mName = name;
        mImplName = implName;
        mConfig = config;

        logger.debug("AAclAuthz: init begins");

        // load access evaluators specified in the config file
        CMSEngine engine = CMS.getCMSEngine();
        EngineConfig mainConfig = engine.getConfig();

        ConfigStore evalConfig = mainConfig.getSubStore(PROP_EVAL, ConfigStore.class);
        ConfigStore i = evalConfig.getSubStore(PROP_IMPL, ConfigStore.class);

        IAccessEvaluator evaluator = null;
        Enumeration<String> mImpls = i.getSubStoreNames();

        while (mImpls.hasMoreElements()) {
            String type = mImpls.nextElement();
            String evalClassPath = null;

            try {
                evalClassPath = i.getString(type + "." + PROP_CLASS);
            } catch (Exception e) {
                logger.error("AAclAuthz: failed to get config class info", e);

                throw new EBaseException(CMS.getUserMessage("CMS_BASE_GET_PROPERTY_FAILED",
                            type + "." + PROP_CLASS));
            }

            // instantiate evaluator
            try {
                evaluator = (IAccessEvaluator) Class.forName(evalClassPath).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new EACLsException(CMS.getUserMessage("CMS_ACL_CLASS_LOAD_FAIL",
                            evalClassPath));
            }

            if (evaluator != null) {
                evaluator.init();
                // store evaluator
                registerEvaluator(type, evaluator);
            } else {
                logger.warn("AAclAuthz: " + CMS.getLogMessage("AUTHZ_EVALUATOR_NULL", type));
            }
        }

        logger.info("AAclAuthz: initialization done");
    }

    /**
     * gets the name of this authorization manager instance
     */
    @Override
    public String getName() {
        return mName;
    }

    /**
     * gets the plugin name of this authorization manager.
     */
    @Override
    public String getImplName() {
        return mImplName;
    }

    /**
     * Parse ACL resource attributes, then update the ACLs memory store
     * This is intended to be used if storing ACLs on ldap is not desired,
     * and the caller is expected to call this method to add resource
     * and acl info into acls memory store. The resACLs format should conform
     * to the following:
     * {@Code <resource ID>:right-1[,right-n]:[allow,deny](right(s))<evaluatorType>=<value>:<comment for this resource acl>}
     *
     * Example: resTurnKnob:left,right:allow(left) group="lefties":door knobs for lefties
     *
     * @param resACLs same format as the resourceACLs attribute
     * @throws EBaseException parsing error from <code>parseACL</code>
     */
    public void addACLs(String resACLs) throws EBaseException {
        ACL acl = ACL.parseACL(resACLs);

        if (acl != null) {
            ACL curACL = mACLs.get(acl.getName());
            if (curACL == null) {
                mACLs.put(acl.getName(), acl);
            } else {
                curACL.merge(acl);
            }
        } else {
            logger.warn("AAclAuthz: parseACL failed");
        }
    }

    @Override
    public void accessInit(String accessInfo) throws EBaseException {
        addACLs(accessInfo);
    }

    @Override
    public ACL getACL(String target) {
        return mACLs.get(target);
    }

    protected Enumeration<String> getTargetNames() {
        return mACLs.keys();
    }

    @Override
    public Enumeration<ACL> getACLs() {
        return mACLs.elements();
    }

    /**
     * Returns the configuration store used by this Authz mgr
     */
    @Override
    public AuthzManagerConfig getConfigStore() {
        return mConfig;
    }

    public String[] getExtendedPluginInfo() {
        return Utils.getStringArrayFromVector(mExtendedPluginInfo);
    }

    /**
     * Returns a list of configuration parameter names.
     * The list is passed to the configuration console so instances of
     * this implementation can be configured through the console.
     *
     * @return String array of configuration parameter names.
     */
    @Override
    public String[] getConfigParams() {
        return mConfigParams;
    }

    /**
     * Registers new handler for the given attribute type
     * in the expressions.
     */
    @Override
    public void registerEvaluator(String type, IAccessEvaluator evaluator) {
        mEvaluators.put(type, evaluator);
        logger.info("AAclAuthz: " + type + " evaluator registered");
    }

    /*******************************************************
     * with session context
     *******************************************************/

    /**
     * Checks if the permission is granted or denied in
     * the current execution context. If the code is
     * marked as privileged, this methods will simply
     * return.
     * <P>
     * note that if a resource does not exist in the aclResources entry, but a higher level node exist, it will still be
     * evaluated. The highest level node's acl determines the permission. If the higher level node doesn't contain any
     * acl information, then it's passed down to the lower node. If a node has no aci in its resourceACLs, then it's
     * considered passed.
     * <p>
     * example: certServer.common.users, if failed permission check for "certServer", then it's considered failed, and
     * there is no need to continue the check. If passed permission check for "certServer", then it's considered passed,
     * and no need to continue the check. If certServer contains no aci then "certServer.common" will be checked for
     * permission instead. If down to the leaf level, the node still contains no aci, then it's considered passed. If at
     * the leaf level, no such resource exist, or no acis, it's considered passed.
     * <p>
     * If there are multiple aci's for a resource, ALL aci's will be checked, and only if all passed permission checks,
     * will the eventual access be granted.
     *
     * @param name resource name
     * @param perm permission requested
     * @exception EACLsException access permission denied
     */
    protected synchronized void checkPermission(String name, String perm)
            throws EACLsException {

        String resource = "";
        StringTokenizer st = new StringTokenizer(name, ".");

        while (st.hasMoreTokens()) {
            String node = st.nextToken();

            if (!"".equals(resource)) {
                resource = resource + "." + node;
            } else {
                resource = node;
            }

            boolean passed = false;

            try {
                passed = checkACLs(resource, perm);

            } catch (EACLsException e) {
                Object[] params = new Object[2];
                params[0] = name;
                params[1] = perm;

                logger.error(CMS.getLogMessage("AUTHZ_EVALUATOR_ACCESS_DENIED", name, perm), e);

                throw new EACLsException(CMS.getUserMessage("CMS_ACL_NO_PERMISSION", (String[]) params));
            }

            if (passed) {
                logger.info("AAclAuthz: Granting " + perm + " permission for " + name);
                return;
            } // else, continue
        }
    }

    /**
     * Checks if the permission is granted or denied in
     * the current execution context.
     * <P>
     * An <code>ACL</code> may contain one or more <code>ACLEntry</code>. However, in case of multiple
     * <code>ACLEntry</code>, a subject must pass ALL of the <code>ACLEntry</code> evaluation for permission to be
     * granted
     * <P>
     * negative ("deny") aclEntries are treated differently than positive ("allow") statements. If a negative aclEntries
     * fails the acl check, the permission check will return "false" right away; while in the case of a positive
     * aclEntry, if the the aclEntry fails the acl check, the next aclEntry will be evaluated.
     *
     * @param name resource name
     * @param perm permission requested
     * @return true if access allowed
     *         false if should be passed down to the next node
     * @exception EACLsException if access disallowed
     */
    private boolean checkACLs(String name, String perm)
            throws EACLsException {
        ACL acl = mACLs.get(name);

        // no such resource, pass it down
        if (acl == null) {
            String infoMsg = "checkACLs(): no acl for" +
                    name + "...pass down to next node";

            logger.info("AAclAuthz: " + infoMsg);

            return false;
        }

        Enumeration<ACLEntry> e = acl.entries();

        if (e == null || !e.hasMoreElements()) {
            // no acis for node, pass down to next node
            String infoMsg = " AAclAuthz.checkACLs(): no acis for " +
                    name + " acl entry...pass down to next node";

            logger.info("AAclAuthz: " + infoMsg);

            return false;
        }

        /**
         * must pass all ACLEntry
         */
        while (e.hasMoreElements()) {
            ACLEntry entry = e.nextElement();

            // if permission not pertinent, move on to next ACLEntry
            if (entry.containPermission(perm)) {
                if (evaluateExpressions(entry.getAttributeExpressions())) {
                    if (!entry.checkPermission(perm)) {
                        logger.error("AAclAuthz: checkACLs(): permission denied");
                        throw new EACLsException(CMS.getUserMessage("CMS_ACL_PERMISSION_DENIED"));
                    }
                } else if (entry.getType() == ACLEntry.Type.ALLOW) {
                    // didn't meet the access expression for "allow", failed
                    logger.error("AAclAuthz: checkACLs(): permission denied");
                    throw new EACLsException(CMS.getUserMessage("CMS_ACL_PERMISSION_DENIED"));
                }
            }
        }

        return true;
    }

    /**
     * Resolves the given expressions.
     * expression || expression || ...
     * example:
     * group="Administrators" || group="Operators"
     */
    private boolean evaluateExpressions(String s) {
        // XXX - just handle "||" (or) among multiple expressions for now
        // XXX - could use some optimization ... later

        logger.debug("evaluating expressions: " + s);

        Vector<Object> v = new Vector<>();

        while (s.length() > 0) {
            int orIndex = s.indexOf("||");
            int andIndex = s.indexOf("&&");

            // this is the last expression
            if (orIndex == -1 && andIndex == -1) {
                boolean passed = evaluateExpression(s.trim());

                v.addElement(Boolean.valueOf(passed));
                break;

                // || first
            } else if (andIndex == -1 || (orIndex != -1 && orIndex < andIndex)) {
                String s1 = s.substring(0, orIndex);
                boolean passed = evaluateExpression(s1.trim());

                v.addElement(Boolean.valueOf(passed));
                v.addElement("||");
                s = s.substring(orIndex + 2);
                // && first
            } else {
                String s1 = s.substring(0, andIndex);
                boolean passed = evaluateExpression(s1.trim());

                v.addElement(Boolean.valueOf(passed));
                v.addElement("&&");
                s = s.substring(andIndex + 2);
            }
        }

        if (v.size() == 1) {
            Boolean bool = (Boolean) v.remove(0);

            return bool.booleanValue();
        }
        boolean left = false;
        String op = "";
        boolean right = false;

        while (!v.isEmpty()) {
            if (op.equals(""))
                left = ((Boolean) v.remove(0)).booleanValue();
            op = (String) v.remove(0);
            right = ((Boolean) v.remove(0)).booleanValue();
            left = evaluateExp(left, op, right);
        }

        return left;
    }

    /**
     * Resolves the given expression.
     */
    private boolean evaluateExpression(String expression) {
        // XXX - just recognize "=" for now!!
        int i = expression.indexOf("=");
        String type = expression.substring(0, i);
        String value = expression.substring(i + 1);
        IAccessEvaluator evaluator = mEvaluators.get(type);

        if (evaluator == null) {
            logger.warn("AAclAuthz: " + CMS.getLogMessage("AUTHZ_EVALUATOR_NOT_FOUND", type));
            return false;
        }

        return evaluator.evaluate(type, "=", value);
    }

    /*******************************************************
     * with authToken
     *******************************************************/

    /**
     * Checks if the permission is granted or denied with id from authtoken
     * gotten from authentication that precedes authorization. If the code is
     * marked as privileged, this methods will simply
     * return.
     * <P>
     * note that if a resource does not exist in the aclResources entry, but a higher level node exist, it will still be
     * evaluated. The highest level node's acl determines the permission. If the higher level node doesn't contain any
     * acl information, then it's passed down to the lower node. If a node has no aci in its resourceACLs, then it's
     * considered passed.
     * <p>
     * example: certServer.common.users, if failed permission check for "certServer", then it's considered failed, and
     * there is no need to continue the check. If passed permission check for "certServer", then it's considered passed,
     * and no need to continue the check. If certServer contains no aci then "certServer.common" will be checked for
     * permission instead. If down to the leaf level, the node still contains no aci, then it's considered passed. If at
     * the leaf level, no such resource exist, or no acis, it's considered passed.
     * <p>
     * If there are multiple aci's for a resource, ALL aci's will be checked, and only if all passed permission checks,
     * will the eventual access be granted.
     *
     * @param authToken authentication token gotten from authentication
     * @param name resource name
     * @param perm permission requested
     * @exception EACLsException access permission denied
     */
    public synchronized void checkPermission(AuthToken authToken, String name,
            String perm)
            throws EACLsException {

        logger.debug("AAclAuthz.checkPermission(" + name + ", " + perm + ")");

        Vector<String> nodes = getNodes(name);
        EvaluationOrder order = getOrder();

        boolean permitted = false;
        if (order == EvaluationOrder.DENY_ALLOW) {
            checkDenyEntries(authToken, nodes, perm);
            permitted = checkAllowEntries(authToken, nodes, perm);
        } else if (order == EvaluationOrder.ALLOW_DENY) {
            permitted = checkAllowEntries(authToken, nodes, perm);
            checkDenyEntries(authToken, nodes, perm);
        }

        if (!permitted) {
            String[] params = new String[2];
            params[0] = name;
            params[1] = perm;

            logger.error(CMS.getLogMessage("AUTHZ_EVALUATOR_ACCESS_DENIED", name, perm));

            throw new EACLsException(CMS.getUserMessage("CMS_ACL_NO_PERMISSION", params));
        }

        logger.info("AAclAuthz: Granting " + perm + " permission for " + name);
    }

    protected boolean checkAllowEntries(
            AuthToken authToken,
            Iterable<String> nodes,
            String perm) {
        for (ACLEntry entry : getEntries(ACLEntry.Type.ALLOW, nodes, perm)) {
            logger.debug("checkAllowEntries(): expressions: " + entry.getAttributeExpressions());
            if (evaluateExpressions(authToken, entry.getAttributeExpressions())) {
                return true;
            }
        }
        return false;
    }

    /** throw EACLsException if a deny entry is matched */
    protected void checkDenyEntries(
            AuthToken authToken,
            Iterable<String> nodes,
            String perm)
            throws EACLsException {
        for (ACLEntry entry : getEntries(ACLEntry.Type.DENY, nodes, perm)) {
            logger.debug("checkDenyEntries(): expressions: " + entry.getAttributeExpressions());
            if (evaluateExpressions(authToken, entry.getAttributeExpressions())) {
                logger.error("AAclAuthz: checkPermission(): permission denied");
                throw new EACLsException(CMS.getUserMessage("CMS_ACL_PERMISSION_DENIED"));
            }
        }
    }

    protected Iterable<ACLEntry> getEntries(
            ACLEntry.Type entryType,
            Iterable<String> nodes,
            String operation
    ) {
        Vector<ACLEntry> v = new Vector<>();

        for (String name : nodes) {
            ACL acl = mACLs.get(name);
            if (acl == null)
                continue;
            Enumeration<ACLEntry> e = acl.entries();
            while (e.hasMoreElements()) {
                ACLEntry entry = e.nextElement();

                if (entry.getType() == entryType &&
                        entry.containPermission(operation)) {
                    v.addElement(entry);
                }
            }
        }

        return v;
    }

    /**
     * Resolves the given expressions.
     * expression || expression || ...
     * example:
     * group="Administrators" || group="Operators"
     */
    private boolean evaluateExpressions(AuthToken authToken, String s) {
        // XXX - just handle "||" (or) among multiple expressions for now
        // XXX - could use some optimization ... later
        logger.debug("evaluating expressions: " + s);

        Vector<Object> v = new Vector<>();

        while (s.length() > 0) {
            int orIndex = s.indexOf("||");
            int andIndex = s.indexOf("&&");

            // this is the last expression
            if (orIndex == -1 && andIndex == -1) {
                boolean passed = evaluateExpression(authToken, s.trim());

                logger.debug("evaluated expression: " + s.trim() + " to be " + passed);
                v.addElement(Boolean.valueOf(passed));
                break;

                // || first
            } else if (andIndex == -1 || (orIndex != -1 && orIndex < andIndex)) {
                String s1 = s.substring(0, orIndex);
                boolean passed = evaluateExpression(authToken, s1.trim());

                logger.debug("evaluated expression: " + s1.trim() + " to be " + passed);
                v.addElement(Boolean.valueOf(passed));
                v.addElement("||");
                s = s.substring(orIndex + 2);
                // && first
            } else {
                String s1 = s.substring(0, andIndex);
                boolean passed = evaluateExpression(authToken, s1.trim());

                logger.debug("evaluated expression: " + s1.trim() + " to be " + passed);
                v.addElement(Boolean.valueOf(passed));
                v.addElement("&&");
                s = s.substring(andIndex + 2);
            }
        }

        if (v.isEmpty()) {
            return false;
        }

        if (v.size() == 1) {
            Boolean bool = (Boolean) v.remove(0);

            return bool.booleanValue();
        }

        boolean left = false;
        String op = "";
        boolean right = false;

        while (!v.isEmpty()) {
            if (op.equals(""))
                left = ((Boolean) v.remove(0)).booleanValue();
            op = (String) v.remove(0);
            right = ((Boolean) v.remove(0)).booleanValue();
            left = evaluateExp(left, op, right);
        }

        return left;
    }

    public Vector<String> getNodes(String resourceID) {
        Vector<String> v = new Vector<>();

        if (resourceID != null && !resourceID.equals("")) {
            v.addElement(resourceID);
        } else {
            return v;
        }
        int index = resourceID.lastIndexOf(".");
        String name = resourceID;

        while (index != -1) {
            name = name.substring(0, index);
            v.addElement(name);
            index = name.lastIndexOf(".");
        }

        return v;
    }

    /**
     * Resolves the given expression.
     */
    private boolean evaluateExpression(AuthToken authToken, String expression) {
        String op = getOp(expression);
        String type = "";
        String value = "";

        if (!op.equals("")) {
            int len = op.length();
            int i = expression.indexOf(op);

            type = expression.substring(0, i).trim();
            value = expression.substring(i + len).trim();
        }
        IAccessEvaluator evaluator = mEvaluators.get(type);

        if (evaluator == null) {
            logger.warn("AAclAuthz: " + CMS.getLogMessage("AUTHZ_EVALUATOR_NOT_FOUND", type));
            return false;
        }

        return evaluator.evaluate(authToken, type, op, value);
    }

    private String getOp(String exp) {
        int i = exp.indexOf("!=");

        if (i == -1) {
            i = exp.indexOf("=");
            if (i == -1) {
                i = exp.indexOf(">");
                if (i == -1) {
                    i = exp.indexOf("<");
                    if (i == -1) {
                        logger.warn("AAclAuthz: " + CMS.getLogMessage("AUTHZ_OP_NOT_SUPPORTED", exp));
                    } else {
                        return "<";
                    }
                } else {
                    return ">";
                }
            } else {
                return "=";
            }
        } else {
            return "!=";
        }
        return "";
    }

    private boolean evaluateExp(boolean left, String op, boolean right) {
        if (op.equals("||")) {
            return left || right;
        } else if (op.equals("&&")) {
            return left && right;
        }
        return false;
    }

    /*******************************************************
     * end identification differentiation
     *******************************************************/

    /**
     * This one only updates the memory. Classes extend this class should
     * also update to a permanent storage
     */
    @Override
    public void updateACLs(String id, String rights, String strACLs,
            String desc) throws EACLsException {
        String resourceACLs = id;

        if (rights != null)
            resourceACLs = id + ":" + rights + ":" + strACLs + ":" + desc;

        // memory update
        ACL ac = null;

        try {
            ac = ACL.parseACL(resourceACLs);
        } catch (EBaseException ex) {
            throw new EACLsException(CMS.getUserMessage("CMS_ACL_PARSING_ERROR_0"));
        }

        mACLs.put(ac.getName(), ac);
    }

    /**
     * gets an enumeration of resources
     *
     * @return an enumeration of resources contained in the ACL table
     */
    public Enumeration<ACL> aclResElements() {
        return mACLs.elements();
    }

    /**
     * gets an enumeration of access evaluators
     *
     * @return an enumeraton of access evaluators
     */
    @Override
    public Enumeration<IAccessEvaluator> aclEvaluatorElements() {
        return (mEvaluators.elements());
    }

    /**
     * gets the access evaluators
     *
     * @return handle to the access evaluators table
     */
    @Override
    public Hashtable<String, IAccessEvaluator> getAccessEvaluators() {
        return mEvaluators;
    }

    /**
     * is this resource name unique
     *
     * @return true if unique; false otherwise
     */
    public boolean isTypeUnique(String type) {
        return !mACLs.containsKey(type);
    }

    /*********************************
     * abstract methods
     **********************************/

    /**
     * check the authorization permission for the user associated with
     * authToken on operation
     *
     * Example:
     *
     * For example, if UsrGrpAdminServlet needs to authorize the
     * caller it would do be done in the following fashion:
     *
     * try {
     *     authzTok = mAuthz.authorize(
     *         "DirAclAuthz", authToken, RES_GROUP, "read");
     * } catch (EBaseException e) {
     *     logger.warn("authorize call: " + e.getMessage(), e);
     * }
     *
     * @param authToken the authToken associated with a user
     * @param resource - the protected resource name
     * @param operation - the protected resource operation name
     * @exception EAuthzAccessDenied If access was denied
     * @exception EAuthzInternalError If an internal error occurred.
     * @return authzToken
     */
    @Override
    public AuthzToken authorize(AuthToken authToken, String resource, String operation)
            throws EAuthzInternalError, EAuthzAccessDenied {
        try {
            checkPermission(authToken, resource, operation);
            // compose AuthzToken
            AuthzToken authzToken = new AuthzToken(this);
            authzToken.set(AuthzToken.TOKEN_AUTHZ_RESOURCE, resource);
            authzToken.set(AuthzToken.TOKEN_AUTHZ_OPERATION, operation);
            authzToken.set(AuthzToken.TOKEN_AUTHZ_STATUS, AuthzToken.AUTHZ_STATUS_SUCCESS);
            logger.debug(mName + ": authorization passed");
            return authzToken;
        } catch (EACLsException e) {
            // audit here later
            logger.error("AAclAuthz: " + CMS.getLogMessage("AUTHZ_EVALUATOR_AUTHORIZATION_FAILED"), e);
            logger.error("AAclAuthz: " + CMS.getLogMessage("AUTHZ_AUTHZ_ACCESS_DENIED_2", resource, operation));

            throw new EAuthzAccessDenied(CMS.getUserMessage("CMS_AUTHORIZATION_ERROR"));
        }
    }

    @Override
    public AuthzToken authorize(AuthToken authToken, String expression)
            throws EAuthzAccessDenied {
        if (evaluateACLs(authToken, expression)) {
            return (new AuthzToken(this));
        }
        String[] params = { expression };
        throw new EAuthzAccessDenied(CMS.getUserMessage("CMS_AUTHORIZATION_AUTHZ_ACCESS_DENIED", params));
    }

    public static EvaluationOrder getOrder() {

        CMSEngine engine = CMS.getCMSEngine();
        EngineConfig cs = engine.getConfig();
        AuthorizationConfig authzConfig = cs.getAuthorizationConfig();

        try {
            String order = authzConfig.getString("evaluateOrder", "");
            return order.startsWith("allow") ? EvaluationOrder.ALLOW_DENY : EvaluationOrder.DENY_ALLOW;
        } catch (Exception e) {
            return EvaluationOrder.DENY_ALLOW;
        }
    }

    public boolean evaluateACLs(AuthToken authToken, String exp) {
        return evaluateExpressions(authToken, exp);
    }
}
