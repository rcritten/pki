/* --- BEGIN COPYRIGHT BLOCK ---
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Copyright (C) 2013 Red Hat, Inc.
 * All rights reserved.
 * --- END COPYRIGHT BLOCK ---
 *
 * @author Endi S. Dewata
 */

var ActivityModel = Model.extend({
    urlRoot: "/tps/rest/activities",
    parseResponse: function(response) {
        return {
            id: response.id,
            tokenID: response.TokenID,
            userID: response.UserID,
            ip: response.IP,
            operation: response.Operation,
            result: response.Result,
            date: response.Date
        };
    },
    createRequest: function(attributes) {
        return {
            id: attributes.id,
            TokenID: attributes.tokenID,
            UserID: attributes.userID,
            IP: attributes.ip,
            Operation: attributes.operation,
            Result: attributes.result,
            Date: attributes.date
        };
    }
});

var ActivityCollection = Collection.extend({
    urlRoot: "/tps/rest/activities",
    getEntries: function(response) {
        return response.entries;
    },
    getLinks: function(response) {
        return response.Link;
    },
    parseEntry: function(entry) {
        return new ActivityModel({
            id: entry.id,
            tokenID: entry.TokenID,
            userID: entry.UserID,
            ip: entry.IP,
            operation: entry.Operation,
            result: entry.Result,
            date: entry.Date
        });
    }
});

var ActivityPage = Page.extend({
    load: function() {
        var editDialog = new Dialog({
            el: $("#activity-dialog"),
            title: "Edit Activity",
            readonly: ["id", "tokenID", "userID", "ip",
                "operation", "result", "date"]
        });

        new Table({
            el: $("table[name='activities']"),
            collection: new ActivityCollection(),
            editDialog: editDialog
        });
    }
});
