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
// (C) 2012 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---

package org.dogtagpki.server.rest;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;

import com.netscape.certsrv.base.BadRequestException;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.PKIException;
import com.netscape.certsrv.base.ResourceNotFoundException;
import com.netscape.certsrv.common.OpDef;
import com.netscape.certsrv.common.ScopeDef;
import com.netscape.certsrv.group.GroupCollection;
import com.netscape.certsrv.group.GroupData;
import com.netscape.certsrv.group.GroupMemberData;
import com.netscape.certsrv.group.GroupNotFoundException;
import com.netscape.certsrv.group.GroupResource;
import com.netscape.certsrv.logging.ILogger;
import com.netscape.certsrv.logging.event.ConfigRoleEvent;
import com.netscape.cms.servlet.admin.GroupMemberProcessor;
import com.netscape.cms.servlet.base.SubsystemService;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.CMSEngine;
import com.netscape.cmscore.usrgrp.Group;
import com.netscape.cmscore.usrgrp.UGSubsystem;

/**
 * @author Endi S. Dewata
 */
public class GroupService extends SubsystemService implements GroupResource {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GroupService.class);

    CMSEngine engine = CMS.getCMSEngine();
    public UGSubsystem userGroupManager = engine.getUGSubsystem();

    public GroupData createGroupData(Group group) throws Exception {

        GroupData groupData = new GroupData();

        String groupID = group.getGroupID();
        if (!StringUtils.isEmpty(groupID)) {
            groupData.setID(groupID);
            groupData.setGroupID(groupID);
        }

        String description = group.getDescription();
        if (!StringUtils.isEmpty(description)) groupData.setDescription(description);

        return groupData;
    }

    /**
     * Searches for users in LDAP directory.
     *
     * Request/Response Syntax:
     * http://warp.mcom.com/server/certificate/columbo/design/
     * ui/admin-protocol-definition.html#user-admin
     */
    @Override
    public Response findGroups(String filter, Integer start, Integer size) {

        if (filter != null && filter.length() < MIN_FILTER_LENGTH) {
            throw new BadRequestException("Filter is too short.");
        }

        start = start == null ? 0 : start;
        size = size == null ? DEFAULT_SIZE : size;

        try {
            Enumeration<Group> groups = userGroupManager.listGroups(filter);

            GroupCollection response = new GroupCollection();
            int i = 0;

            // skip to the start of the page
            for ( ; i<start && groups.hasMoreElements(); i++) groups.nextElement();

            // return entries up to the page size
            for ( ; i<start+size && groups.hasMoreElements(); i++) {
                Group group = groups.nextElement();
                response.addEntry(createGroupData(group));
            }

            // count the total entries
            for ( ; groups.hasMoreElements(); i++) groups.nextElement();
            response.setTotal(i);

            return createOKResponse(response);

        } catch (Exception e) {
            logger.error("GroupService: " + e.getMessage(), e);
            throw new PKIException(e);
        }
    }

    /**
     * finds a group
     * Request/Response Syntax:
     * http://warp.mcom.com/server/certificate/columbo/design/
     * ui/admin-protocol-definition.html#user-admin
     */
    @Override
    public Response getGroup(String groupID) {
        return createOKResponse(getGroupData(groupID));
    }

    public GroupData getGroupData(String groupID) {

        try {
            if (groupID == null) {
                logger.error(CMS.getLogMessage("ADMIN_SRVLT_NULL_RS_ID"));
                throw new BadRequestException(getUserMessage("CMS_ADMIN_SRVLT_NULL_RS_ID", headers));
            }

            Group group = userGroupManager.getGroupFromName(groupID);
            if (group == null) {
                logger.error(CMS.getLogMessage("USRGRP_SRVLT_GROUP_NOT_EXIST"));
                throw new GroupNotFoundException(groupID);
            }

            return createGroupData(group);

        } catch (PKIException e) {
            throw e;

        } catch (Exception e) {
            throw new PKIException(getUserMessage("CMS_INTERNAL_ERROR", headers));
        }
    }

    /**
     * Adds a new group in local scope.
     * <P>
     *
     * Request/Response Syntax: http://warp.mcom.com/server/certificate/columbo/design/
     * ui/admin-protocol-definition.html#group
     * <P>
     *
     * <ul>
     * <li>signed.audit LOGGING_SIGNED_AUDIT_CONFIG_ROLE used when configuring role information (anything under
     * users/groups)
     * </ul>
     */
    @Override
    public Response addGroup(GroupData groupData) {

        if (groupData == null) throw new BadRequestException("Group data is null.");

        String groupID = groupData.getGroupID();

        // ensure that any low-level exceptions are reported
        // to the signed audit log and stored as failures
        try {
            if (groupID == null) {
                logger.error(CMS.getLogMessage("ADMIN_SRVLT_NULL_RS_ID"));
                throw new BadRequestException(getUserMessage("CMS_ADMIN_SRVLT_NULL_RS_ID", headers));
            }

            Group group = userGroupManager.createGroup(groupID);

            // add description if specified
            String description = groupData.getDescription();
            if (description != null && !description.equals("")) {
                group.set(Group.ATTR_DESCRIPTION, description);
            }

            // allow adding a group with no members
            userGroupManager.addGroup(group);

            auditAddGroup(groupID, groupData, ILogger.SUCCESS);

            // read the data back
            groupData = getGroupData(groupID);

            String encodedGroupID = URLEncoder.encode(groupData.getID(), "UTF-8");
            URI uri = uriInfo
                    .getBaseUriBuilder()
                    .path(GroupResource.class)
                    .path("{groupID}")
                    .build(encodedGroupID);
            return createCreatedResponse(groupData, uri);

        } catch (PKIException e) {
            auditAddGroup(groupID, groupData, ILogger.FAILURE);
            throw e;

        } catch (EBaseException | UnsupportedEncodingException e) {
            auditAddGroup(groupID, groupData, ILogger.FAILURE);
            throw new PKIException(e.getMessage());
        }
    }

    /**
     * modifies a group
     * <P>
     *
     * last person of the super power group "Certificate Server Administrators" can never be removed.
     * <P>
     *
     * http://warp.mcom.com/server/certificate/columbo/design/ ui/admin-protocol-definition.html#group
     * <P>
     *
     * <ul>
     * <li>signed.audit LOGGING_SIGNED_AUDIT_CONFIG_ROLE used when configuring role information (anything under
     * users/groups)
     * </ul>
     */
    @Override
    public Response modifyGroup(String groupID, GroupData groupData) {

        if (groupData == null) throw new BadRequestException("Group data is null.");

        // ensure that any low-level exceptions are reported
        // to the signed audit log and stored as failures
        try {
            if (groupID == null) {
                logger.error(CMS.getLogMessage("ADMIN_SRVLT_NULL_RS_ID"));
                throw new BadRequestException(getUserMessage("CMS_ADMIN_SRVLT_NULL_RS_ID", headers));
            }

            Group group = userGroupManager.getGroupFromName(groupID);

            if (group == null) {
                throw new ResourceNotFoundException("Group " + groupID + "  not found.");
            }

            // update description if specified
            String description = groupData.getDescription();
            if (description != null) {
                if (description.equals("")) { // remove value if empty
                    group.delete(Group.ATTR_DESCRIPTION);
                } else { // otherwise replace value
                    group.set(Group.ATTR_DESCRIPTION, description);
                }
            }

            // allow adding a group with no members, except "Certificate
            // Server Administrators"
            userGroupManager.modifyGroup(group);

            auditModifyGroup(groupID, groupData, ILogger.SUCCESS);

            // read the data back
            groupData = getGroupData(groupID);

            return createOKResponse(groupData);

        } catch (PKIException e) {
            auditModifyGroup(groupID, groupData, ILogger.FAILURE);
            throw e;

        } catch (EBaseException e) {
            auditModifyGroup(groupID, groupData, ILogger.FAILURE);
            throw new PKIException(e.getMessage());
        }
    }

    /**
     * removes a group
     * <P>
     *
     * Request/Response Syntax: http://warp.mcom.com/server/certificate/columbo/design/
     * ui/admin-protocol-definition.html#group
     * <P>
     *
     * <ul>
     * <li>signed.audit LOGGING_SIGNED_AUDIT_CONFIG_ROLE used when configuring role information (anything under
     * users/groups)
     * </ul>
     */
    @Override
    public Response removeGroup(String groupID) {

        // ensure that any low-level exceptions are reported
        // to the signed audit log and stored as failures
        try {
            if (groupID == null) {
                logger.error(CMS.getLogMessage("ADMIN_SRVLT_NULL_RS_ID"));
                throw new BadRequestException(getUserMessage("CMS_ADMIN_SRVLT_NULL_RS_ID", headers));
            }

            // if fails, let the exception fall through
            userGroupManager.removeGroup(groupID);

            auditDeleteGroup(groupID, ILogger.SUCCESS);

            return createNoContentResponse();

        } catch (PKIException e) {
            auditDeleteGroup(groupID, ILogger.FAILURE);
            throw e;

        } catch (EBaseException e) {
            auditDeleteGroup(groupID, ILogger.FAILURE);
            throw new PKIException(e.getMessage());
        }
    }

    @Override
    public Response findGroupMembers(String groupID, String filter, Integer start, Integer size) {

        logger.debug("GroupService.findGroupMembers(" + groupID + ", " + filter + ")");

        if (groupID == null) throw new BadRequestException("Group ID is null.");

        if (filter != null && filter.length() < 3) {
            throw new BadRequestException("Filter is too short.");
        }

        try {
            GroupMemberProcessor processor = new GroupMemberProcessor(getLocale(headers));
            processor.setUriInfo(uriInfo);
            return createOKResponse(processor.findGroupMembers(groupID, filter, start, size));

        } catch (PKIException e) {
            throw e;

        } catch (Exception e) {
            e.printStackTrace();
            throw new PKIException(e.getMessage(), e);
        }
    }

    @Override
    public Response getGroupMember(String groupID, String memberID) {

        if (groupID == null) throw new BadRequestException("Group ID is null.");
        if (memberID == null) throw new BadRequestException("Member ID is null.");

        try {
            GroupMemberProcessor processor = new GroupMemberProcessor(getLocale(headers));
            processor.setUriInfo(uriInfo);
            return createOKResponse(processor.getGroupMember(groupID, memberID));

        } catch (PKIException e) {
            throw e;

        } catch (Exception e) {
            e.printStackTrace();
            throw new PKIException(e.getMessage(), e);
        }
    }

    @Override
    public Response addGroupMember(String groupID, GroupMemberData groupMemberData) {
        if (groupID == null) throw new BadRequestException("Group ID is null.");
        if (groupMemberData.getID() == null) throw new BadRequestException("Member ID is null.");
        groupMemberData.setGroupID(groupID);

        try {
            GroupMemberProcessor processor = new GroupMemberProcessor(getLocale(headers));
            processor.setUriInfo(uriInfo);
            groupMemberData = processor.addGroupMember(groupMemberData);

            String encodedGroupID = URLEncoder.encode(groupID, "UTF-8");
            String encodedGroupMemberID = URLEncoder.encode(groupMemberData.getID(), "UTF-8");
            URI uri = uriInfo.getBaseUriBuilder()
                    .path(GroupResource.class)
                    .path("{groupID}/members/{memberID}")
                    .build(encodedGroupID, encodedGroupMemberID);
            return createCreatedResponse(groupMemberData, uri);

        } catch (PKIException e) {
            throw e;

        } catch (Exception e) {
            e.printStackTrace();
            throw new PKIException(e.getMessage(), e);
        }
    }

    @Override
    public Response removeGroupMember(String groupID, String memberID) {

        if (groupID == null) throw new BadRequestException("Group ID is null.");
        if (memberID == null) throw new BadRequestException("Member ID is null.");

        try {
            GroupMemberProcessor processor = new GroupMemberProcessor(getLocale(headers));
            processor.setUriInfo(uriInfo);
            processor.removeGroupMember(groupID, memberID);

            return createNoContentResponse();

        } catch (PKIException e) {
            throw e;

        } catch (Exception e) {
            e.printStackTrace();
            throw new PKIException(e.getMessage(), e);
        }
    }

    public void auditAddGroup(String groupID, GroupData groupData, String status) {
        audit(OpDef.OP_ADD, groupID, getParams(groupData), status);
    }

    public void auditModifyGroup(String groupID, GroupData groupData, String status) {
        audit(OpDef.OP_MODIFY, groupID, getParams(groupData), status);
    }

    public void auditDeleteGroup(String groupID, String status) {
        audit(OpDef.OP_DELETE, groupID, null, status);
    }

    public void audit(String type, String id, Map<String, String> params, String status) {

        if (auditor == null) return;

        signedAuditLogger.log(new ConfigRoleEvent(
                auditor.getSubjectID(),
                status,
                auditor.getParamString(ScopeDef.SC_GROUPS, type, id, params)));
    }
}
