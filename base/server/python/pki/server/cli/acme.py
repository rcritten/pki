# Authors:
#     Endi S. Dewata <edewata@redhat.com>
#
# Copyright Red Hat, Inc.
#
# SPDX-License-Identifier: GPL-2.0-or-later
#

from __future__ import absolute_import
from __future__ import print_function
import getopt
import logging
import os
import sys

import pki.cli
import pki.server
import pki.server.instance
import pki.server.cli.subsystem

# TODO: auto-populate this map from /usr/share/pki/acme/database
DATABASE_CLASSES = {
    'ds': 'org.dogtagpki.acme.database.DSDatabase',
    'in-memory': 'org.dogtagpki.acme.database.InMemoryDatabase',
    'ldap': 'org.dogtagpki.acme.database.LDAPDatabase',
    'openldap': 'org.dogtagpki.acme.database.OpenLDAPDatabase',
    'postgresql': 'org.dogtagpki.acme.database.PostgreSQLDatabase'
}

DATABASE_TYPES = {value: key for key, value in DATABASE_CLASSES.items()}

# TODO: auto-populate this map from /usr/share/pki/acme/issuer
ISSUER_CLASSES = {
    'nss': 'org.dogtagpki.acme.issuer.NSSIssuer',
    'pki': 'org.dogtagpki.acme.issuer.PKIIssuer'
}

ISSUER_TYPES = {value: key for key, value in ISSUER_CLASSES.items()}

# TODO: auto-populate this map from /usr/share/pki/acme/realm
REALM_CLASSES = {
    'ds': 'org.dogtagpki.acme.realm.DSRealm',
    'in-memory': 'org.dogtagpki.acme.realm.InMemoryRealm',
    'postgresql': 'org.dogtagpki.acme.realm.PostgreSQLRealm'
}

REALM_TYPES = {value: key for key, value in REALM_CLASSES.items()}

logger = logging.getLogger(__name__)


class ACMECLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('acme', 'ACME management commands')

        self.add_module(ACMECreateCLI())
        self.add_module(ACMERemoveCLI())
        self.add_module(pki.server.cli.subsystem.SubsystemDeployCLI(self))
        self.add_module(pki.server.cli.subsystem.SubsystemUndeployCLI(self))

        self.add_module(ACMEMetadataCLI())
        self.add_module(ACMEDatabaseCLI())
        self.add_module(ACMEIssuerCLI())
        self.add_module(ACMERealmCLI())


class ACMECreateCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('create', 'Create ACME subsystem')

    def print_help(self):
        print('Usage: pki-server acme-create [OPTIONS] [name]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --force                        Force creation.')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=', 'database=', 'issuer=',
                'force',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        name = 'acme'
        instance_name = 'pki-tomcat'
        force = False

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--force':
                force = True

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) > 0:
            name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, name)
        instance.makedirs(acme_conf_dir, force=force)

        acme_share_dir = os.path.join(pki.server.PKIServer.SHARE_DIR, 'acme')

        database_template = os.path.join(acme_share_dir, 'conf', 'database.conf')
        database_conf = os.path.join(acme_conf_dir, 'database.conf')
        instance.copy(database_template, database_conf, force=force)

        issuer_template = os.path.join(acme_share_dir, 'conf', 'issuer.conf')
        issuer_conf = os.path.join(acme_conf_dir, 'issuer.conf')
        instance.copy(issuer_template, issuer_conf, force=force)

        realm_template = os.path.join(acme_share_dir, 'conf', 'realm.conf')
        realm_conf = os.path.join(acme_conf_dir, 'realm.conf')
        instance.copy(realm_template, realm_conf, force=force)


class ACMERemoveCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('remove', 'Remove ACME subsystem')

    def print_help(self):
        print('Usage: pki-server acme-remove [OPTIONS] [name]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --force                        Force removal.')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'force',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        name = 'acme'
        instance_name = 'pki-tomcat'
        force = False

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--force':
                force = True

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) > 0:
            name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, name)
        logger.info('Removing %s', acme_conf_dir)
        pki.util.rmtree(acme_conf_dir, force=force)


class ACMEDeployCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('deploy', 'Deploy ACME subsystem')

    def print_help(self):
        print('Usage: pki-server acme-deploy [OPTIONS] [name]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --wait                         Wait until started.')
        print('      --max-wait <seconds>           Maximum wait time (default: 60).')
        print('      --timeout <seconds>            Connection timeout.')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'wait', 'max-wait=', 'timeout=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        name = 'acme'
        instance_name = 'pki-tomcat'
        wait = False
        max_wait = 60
        timeout = None

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--wait':
                wait = True

            elif o == '--max-wait':
                max_wait = int(a)

            elif o == '--timeout':
                timeout = int(a)

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) > 0:
            name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        descriptor = os.path.join(pki.server.PKIServer.SHARE_DIR,
                                  'acme/conf/Catalina/localhost/acme.xml')
        doc_base = os.path.join(pki.server.PKIServer.SHARE_DIR,
                                'acme/webapps/acme')

        logger.info('Deploying %s webapp', name)
        instance.deploy_webapp(
            name,
            descriptor,
            doc_base,
            wait=wait,
            max_wait=max_wait,
            timeout=timeout)


class ACMEUndeployCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('undeploy', 'Undeploy ACME subsystem')

    def print_help(self):
        print('Usage: pki-server acme-undeploy [OPTIONS] [name]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --wait                         Wait until stopped.')
        print('      --max-wait <seconds>           Maximum wait time (default: 60).')
        print('      --timeout <seconds>            Connection timeout.')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, args = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'wait', 'max-wait=', 'timeout=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        name = 'acme'
        instance_name = 'pki-tomcat'
        wait = False
        max_wait = 60
        timeout = None

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--wait':
                wait = True

            elif o == '--max-wait':
                max_wait = int(a)

            elif o == '--timeout':
                timeout = int(a)

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        if len(args) > 0:
            name = args[0]

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        logger.info('Undeploying %s webapp', name)
        instance.undeploy_webapp(
            name,
            wait=wait,
            max_wait=max_wait,
            timeout=timeout)


class ACMEMetadataCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('metadata', 'ACME metadata management commands')

        self.add_module(ACMEMetadataShowCLI())
        self.add_module(ACMEMetadataModifyCLI())


class ACMEMetadataShowCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('show', 'Show ACME metadata configuration')

    def print_help(self):
        print('Usage: pki-server acme-metadata-show [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, 'acme')
        metadata_conf = os.path.join(acme_conf_dir, 'metadata.conf')
        config = {}

        if not os.path.exists(metadata_conf):
            source = '/usr/share/pki/acme/conf/metadata.conf'
        else:
            source = metadata_conf

        logger.info('Loading %s', source)
        pki.util.load_properties(source, config)

        terms_of_service = config.get('termsOfService')
        if terms_of_service:
            print('  Terms of Service: %s' % terms_of_service)

        website = config.get('website')
        if website:
            print('  Website: %s' % website)

        caa_identities = config.get('caaIdentities')
        if caa_identities:
            print('  CAA Identities: %s' % caa_identities)

        external_account_required = config.get('externalAccountRequired')
        if external_account_required:
            print('  External Account Required: %s' % external_account_required)


class ACMEMetadataModifyCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('mod', 'Modify ACME metadata configuration')

    def print_help(self):
        print('Usage: pki-server acme-metadata-mod [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, 'acme')
        metadata_conf = os.path.join(acme_conf_dir, 'metadata.conf')
        config = {}

        if not os.path.exists(metadata_conf):
            source = '/usr/share/pki/acme/conf/metadata.conf'
        else:
            source = metadata_conf

        logger.info('Loading %s', source)
        pki.util.load_properties(source, config)

        print('The current value is displayed in the square brackets.')
        print('To keep the current value, simply press Enter.')
        print('To change the current value, enter the new value.')
        print('To remove the current value, enter a blank space.')

        print()
        print('Enter the location of the terms of service.')
        terms_of_service = config.get('termsOfService')
        terms_of_service = pki.util.read_text('  Terms of Service', default=terms_of_service)
        pki.util.set_property(config, 'termsOfService', terms_of_service)

        print()
        print('Enter the location of the website.')
        website = config.get('website')
        website = pki.util.read_text('  Website', default=website)
        pki.util.set_property(config, 'website', website)

        print()
        print('Enter the CAA identities.')
        caa_identities = config.get('caaIdentities')
        caa_identities = pki.util.read_text('  CAA Identities', default=caa_identities)
        pki.util.set_property(config, 'caaIdentities', caa_identities)

        print()
        print('Enter true/false whether an external account is required.')
        external_account_required = config.get('externalAccountRequired')
        external_account_required = pki.util.read_text(
            '  External Account Required', default=external_account_required)
        pki.util.set_property(config, 'externalAccountRequired', external_account_required)

        instance.store_properties(metadata_conf, config)


class ACMEDatabaseCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('database', 'ACME database management commands')

        self.add_module(ACMEDatabaseShowCLI())
        self.add_module(ACMEDatabaseModifyCLI())


class ACMEDatabaseShowCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('show', 'Show ACME database configuration')

    def print_help(self):
        print('Usage: pki-server acme-database-show [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, 'acme')
        database_conf = os.path.join(acme_conf_dir, 'database.conf')
        config = {}

        logger.info('Loading %s', database_conf)
        pki.util.load_properties(database_conf, config)

        database_class = config.get('class')

        database_type = DATABASE_TYPES.get(database_class)
        print('  Database Type: %s' % database_type)

        if database_type in ['ds', 'ldap', 'openldap']:

            url = config.get('url')
            if url:
                print('  Server URL: %s' % url)

            auth_type = config.get('authType')
            if auth_type:
                print('  Authentication Type: %s' % auth_type)

            if auth_type == 'BasicAuth':

                bind_dn = config.get('bindDN')
                if bind_dn:
                    print('  Bind DN: %s' % bind_dn)

                password = config.get('bindPassword')
                if password:
                    print('  Bind Password: ********')

            elif auth_type == 'SslClientAuth':

                nickname = config.get('nickname')
                if nickname:
                    print('  Client Certificate: %s' % nickname)

            base_dn = config.get('basedn')
            if base_dn:
                logger.warning('The basedn parameter has been deprecated. Use baseDN instead.')
            else:
                base_dn = config.get('baseDN')

            if base_dn:
                print('  Base DN: %s' % base_dn)

        elif database_type == 'postgresql':

            url = config.get('url')
            if url:
                print('  Server URL: %s' % url)

            username = config.get('user')
            if username:
                print('  Username: %s' % username)

            password = config.get('password')
            if password:
                print('  Password: ********')


class ACMEDatabaseModifyCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('mod', 'Modify ACME database configuration')

    def print_help(self):
        print('Usage: pki-server acme-database-mod [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --type <type>                  Database type: {0}'
              .format(', '.join(DATABASE_TYPES.values())))
        print('      -D<name>=<value>               Set property value.')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:vD:', [
                'instance=', 'type=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        database_type = None
        props = {}

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--type':
                database_type = a
                if database_type not in DATABASE_TYPES.values():
                    raise Exception('Invalid database type: {0}'.format(database_type))

            elif o == '-D':
                parts = a.split('=', 1)
                name = parts[0]
                value = parts[1]
                props[name] = value

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, 'acme')
        database_conf = os.path.join(acme_conf_dir, 'database.conf')
        config = {}

        if database_type:
            # if --type is specified, load the database.conf template
            source = '/usr/share/pki/acme/database/{0}/database.conf'.format(database_type)
        else:
            # otherwise, load the database.conf from the instance
            source = database_conf

        logger.info('Loading %s', source)
        pki.util.load_properties(source, config)

        # if --type or -D is specified, use silent mode
        if database_type or props:

            logger.info('Setting properties:')
            for name, value in props.items():
                logger.info('- %s: %s', name, value)
                pki.util.set_property(config, name, value)

            instance.store_properties(database_conf, config)
            return

        # otherwise, use interactive mode

        print('The current value is displayed in the square brackets.')
        print('To keep the current value, simply press Enter.')
        print('To change the current value, enter the new value.')
        print('To remove the current value, enter a blank space.')

        database_class = config.get('class')

        print()
        print(
            'Enter the type of the database. '
            'Available types: %s.' % ', '.join(DATABASE_TYPES.values()))
        database_type = DATABASE_TYPES.get(database_class)
        orig_database_type = database_type

        database_type = pki.util.read_text(
            '  Database Type',
            options=DATABASE_TYPES.values(),
            default=database_type,
            required=True)
        pki.util.set_property(config, 'class', DATABASE_CLASSES.get(database_type))

        if orig_database_type != database_type:
            source = '/usr/share/pki/acme/database/{0}/database.conf'.format(database_type)
            logger.info('Loading %s', source)
            pki.util.load_properties(source, config)

        if database_type == 'in-memory':
            config.pop('url', None)
            config.pop('user', None)
            config.pop('password', None)

        elif database_type in ['ds', 'ldap', 'openldap']:

            print()
            print('Enter the location of the LDAP server '
                  '(e.g. ldap://localhost.localdomain:389).')
            url = config.get('url')
            url = pki.util.read_text('  Server URL', default=url, required=True)
            pki.util.set_property(config, 'url', url)

            print()
            print('Enter the authentication type. Available types: BasicAuth, SslClientAuth.')
            auth_type = config.get('authType')
            auth_type = pki.util.read_text(
                '  Authentication Type',
                options=['BasicAuth', 'SslClientAuth'],
                default=auth_type,
                required=True)
            pki.util.set_property(config, 'authType', auth_type)

            if auth_type == 'BasicAuth':

                print()
                print('Enter the bind DN.')
                bind_dn = config.get('bindDN')
                bind_dn = pki.util.read_text('  Bind DN', default=bind_dn, required=True)
                pki.util.set_property(config, 'bindDN', bind_dn)

                print()
                print('Enter the bind password.')
                password = config.get('bindPassword')
                password = pki.util.read_text(
                    '  Bind Password',
                    default=password,
                    password=True,
                    required=True)
                pki.util.set_property(config, 'bindPassword', password)

            elif auth_type == 'SslClientAuth':

                print()
                print('Enter the client certificate.')
                nickname = config.get('nickname')
                nickname = pki.util.read_text(
                    '  Client Certificate',
                    default=nickname,
                    required=True)
                pki.util.set_property(config, 'nickname', nickname)

            print()
            print('Enter the base DN for the ACME subtree.')

            base_dn = config.pop('basedn', None)
            if not base_dn:
                base_dn = config.get('baseDN')

            base_dn = pki.util.read_text('  Base DN', default=base_dn, required=True)
            pki.util.set_property(config, 'baseDN', base_dn)

        elif database_type == 'postgresql':

            print()
            print('Enter the location of the PostgreSQL database '
                  '(e.g. jdbc:postgresql://localhost.localdomain:5432/acme).')
            url = config.get('url')
            url = pki.util.read_text('  Server URL', default=url, required=True)
            pki.util.set_property(config, 'url', url)

            print()
            print('Enter the username for basic authentication.')
            username = config.get('user')
            username = pki.util.read_text('  Username', default=username, required=True)
            pki.util.set_property(config, 'user', username)

            print()
            print('Enter the password for basic authentication.')
            password = config.get('password')
            password = pki.util.read_text(
                '  Password', default=password, password=True, required=True)
            pki.util.set_property(config, 'password', password)

        instance.store_properties(database_conf, config)


class ACMEIssuerCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('issuer', 'ACME issuer management commands')

        self.add_module(ACMEIssuerShowCLI())
        self.add_module(ACMEIssuerModifyCLI())


class ACMEIssuerShowCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('show', 'Show ACME issuer configuration')

    def print_help(self):
        print('Usage: pki-server acme-issuer-show [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, 'acme')
        issuer_conf = os.path.join(acme_conf_dir, 'issuer.conf')
        config = {}

        logger.info('Loading %s', issuer_conf)
        pki.util.load_properties(issuer_conf, config)

        issuer_class = config.get('class')

        issuer_type = ISSUER_TYPES.get(issuer_class)
        print('  Issuer Type: %s' % issuer_type)

        if issuer_type == 'nss':

            nickname = config.get('nickname')
            if nickname:
                print('  Signing Certificate: %s' % nickname)

            extensions = config.get('extensions')
            if extensions:
                print('  Certificate Extensions: %s' % extensions)

        elif issuer_type == 'pki':

            url = config.get('url')
            if url:
                print('  Server URL: %s' % url)

            nickname = config.get('nickname')
            if nickname:
                print('  Client Certificate: %s' % nickname)

            username = config.get('username')
            if username:
                print('  Agent Username: %s' % username)

            password = config.get('password')
            if password:
                print('  Agent Password: ********')

            password_file = config.get('passwordFile')
            if password_file:
                print('  Password file: %s' % password_file)

            profile = config.get('profile')
            if profile:
                print('  Certificate Profile: %s' % profile)


class ACMEIssuerModifyCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('mod', 'Modify ACME issuer configuration')

    def print_help(self):
        print('Usage: pki-server acme-issuer-mod [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --type <type>                  Issuer type: {0}'
              .format(', '.join(ISSUER_TYPES.values())))
        print('      -D<name>=<value>               Set property value.')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:vD:', [
                'instance=', 'type=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        issuer_type = None
        props = {}

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--type':
                issuer_type = a
                if issuer_type not in ISSUER_TYPES.values():
                    raise Exception('Invalid issuer type: {0}'.format(issuer_type))

            elif o == '-D':
                parts = a.split('=', 1)
                name = parts[0]
                value = parts[1]
                props[name] = value

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, 'acme')
        issuer_conf = os.path.join(acme_conf_dir, 'issuer.conf')
        config = {}

        if issuer_type:
            # if --type is specified, load the issuer.conf template
            source = '/usr/share/pki/acme/issuer/{0}/issuer.conf'.format(issuer_type)
        else:
            # otherwise, load the issuer.conf from the instance
            source = issuer_conf

        logger.info('Loading %s', source)
        pki.util.load_properties(source, config)

        # if --type or -D is specified, use silent mode
        if issuer_type or props:

            logger.info('Setting properties:')
            for name, value in props.items():
                logger.info('- %s: %s', name, value)
                pki.util.set_property(config, name, value)

            instance.store_properties(issuer_conf, config)
            return

        # otherwise, use interactive mode

        print('The current value is displayed in the square brackets.')
        print('To keep the current value, simply press Enter.')
        print('To change the current value, enter the new value.')
        print('To remove the current value, enter a blank space.')

        issuer_class = config.get('class')

        print()
        print(
            'Enter the type of the certificate issuer. '
            'Available types: %s.' % ', '.join(ISSUER_TYPES.values()))
        issuer_type = ISSUER_TYPES.get(issuer_class)
        orig_issuer_type = issuer_type

        issuer_type = pki.util.read_text(
            '  Issuer Type',
            options=ISSUER_TYPES.values(),
            default=issuer_type,
            required=True)
        pki.util.set_property(config, 'class', ISSUER_CLASSES.get(issuer_type))

        if orig_issuer_type != issuer_type:
            source = '/usr/share/pki/acme/issuer/{0}/issuer.conf'.format(issuer_type)
            logger.info('Loading %s', source)
            pki.util.load_properties(source, config)

        if issuer_type == 'nss':

            print()
            print('Enter the nickname of the signing certificate.')
            nickname = config.get('nickname')
            nickname = pki.util.read_text('  Signing Certificate', default=nickname)
            pki.util.set_property(config, 'nickname', nickname)

            print()
            print('Enter the certificate extension configuration.')
            extensions = config.get('extensions')
            extensions = pki.util.read_text('  Certificate Extensions', default=extensions)
            pki.util.set_property(config, 'extensions', extensions)

        elif issuer_type == 'pki':

            print()
            print('Enter the location of the PKI server '
                  '(e.g. https://localhost.localdomain:8443).')
            url = config.get('url')
            url = pki.util.read_text('  Server URL', default=url, required=True)
            pki.util.set_property(config, 'url', url)

            print()
            print('Enter the certificate nickname for client authentication.')
            print('This might be the CA agent certificate.')
            print('Enter blank to use basic authentication.')
            nickname = config.get('nickname')
            nickname = pki.util.read_text('  Client Certificate', default=nickname)
            pki.util.set_property(config, 'nickname', nickname)

            print()
            print('Enter the username of the CA agent for basic authentication.')
            print('Enter blank if a CA agent certificate is used for client authentication.')
            username = config.get('username')
            username = pki.util.read_text('  Agent Username', default=username)
            pki.util.set_property(config, 'username', username)

            print()
            print('Enter the CA agent password for basic authentication.')
            print('Enter blank if the password is already stored in a separate property file')
            print('or if a CA agent certificate is used for client authentication.')
            password = config.get('password')
            password = pki.util.read_text('  Agent Password', default=password, password=True)
            pki.util.set_property(config, 'password', password)

            if password:
                config.pop('passwordFile', None)
            else:
                print()
                print('Enter the property file that stores the CA agent password.')
                print('The password must be stored under acmeUserPassword property.')
                password_file = config.get('passwordFile')
                password_file = pki.util.read_text('  Password File', default=password_file)
                pki.util.set_property(config, 'passwordFile', password_file)

            print()
            print('Enter the certificate profile for issuing ACME certificates '
                  '(e.g. acmeServerCert).')
            profile = config.get('profile')
            profile = pki.util.read_text('  Certificate Profile', default=profile, required=True)
            pki.util.set_property(config, 'profile', profile)

        instance.store_properties(issuer_conf, config)


class ACMERealmCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('realm', 'ACME realm management commands')

        self.add_module(ACMERealmShowCLI())
        self.add_module(ACMERealmModifyCLI())


class ACMERealmShowCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('show', 'Show ACME realm configuration')

    def print_help(self):
        print('Usage: pki-server acme-realm-show [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:v', [
                'instance=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, 'acme')
        realm_conf = os.path.join(acme_conf_dir, 'realm.conf')
        config = {}

        logger.info('Loading %s', realm_conf)
        pki.util.load_properties(realm_conf, config)

        realm_class = config.get('class')

        realm_type = REALM_TYPES.get(realm_class)
        print('  Realm Type: %s' % realm_type)

        if realm_type == 'in-memory':
            username = config.get('username')
            if username:
                print('  Admin Username: %s' % username)

            password = config.get('password')
            if password:
                print('  Admin Password: ********')

        elif realm_type == 'ds':

            url = config.get('url')
            if url:
                print('  Server URL: %s' % url)

            auth_type = config.get('authType')
            if auth_type:
                print('  Authentication Type: %s' % auth_type)

            if auth_type == 'BasicAuth':

                bind_dn = config.get('bindDN')
                if bind_dn:
                    print('  Bind DN: %s' % bind_dn)

                password = config.get('bindPassword')
                if password:
                    print('  Bind Password: ********')

            elif auth_type == 'SslClientAuth':

                nickname = config.get('nickname')
                if nickname:
                    print('  Client Certificate: %s' % nickname)

            users_dn = config.get('usersDN')

            if users_dn:
                print('  Users DN: %s' % users_dn)

            groups_dn = config.get('groupsDN')

            if groups_dn:
                print('  Groups DN: %s' % groups_dn)

        elif realm_type == 'postgresql':

            url = config.get('url')
            if url:
                print('  Server URL: %s' % url)

            username = config.get('user')
            if username:
                print('  Username: %s' % username)

            password = config.get('password')
            if password:
                print('  Password: ********')


class ACMERealmModifyCLI(pki.cli.CLI):

    def __init__(self):
        super().__init__('mod', 'Modify ACME realm configuration')

    def print_help(self):
        print('Usage: pki-server acme-realm-mod [OPTIONS]')
        print()
        print('  -i, --instance <instance ID>       Instance ID (default: pki-tomcat).')
        print('      --type <type>                  Realm type: {0}'
              .format(', '.join(REALM_TYPES.values())))
        print('      -D<name>=<value>               Set property value.')
        print('  -v, --verbose                      Run in verbose mode.')
        print('      --debug                        Run in debug mode.')
        print('      --help                         Show help message.')
        print()

    def execute(self, argv):

        try:
            opts, _ = getopt.gnu_getopt(argv, 'i:vD:', [
                'instance=', 'type=',
                'verbose', 'debug', 'help'])

        except getopt.GetoptError as e:
            logger.error(e)
            self.print_help()
            sys.exit(1)

        instance_name = 'pki-tomcat'
        realm_type = None
        props = {}

        for o, a in opts:
            if o in ('-i', '--instance'):
                instance_name = a

            elif o == '--type':
                realm_type = a
                if realm_type not in REALM_TYPES.values():
                    raise Exception('Invalid realm type: {0}'.format(realm_type))

            elif o == '-D':
                parts = a.split('=', 1)
                name = parts[0]
                value = parts[1]
                props[name] = value

            elif o in ('-v', '--verbose'):
                logging.getLogger().setLevel(logging.INFO)

            elif o == '--debug':
                logging.getLogger().setLevel(logging.DEBUG)

            elif o == '--help':
                self.print_help()
                sys.exit()

            else:
                logger.error('Unknown option: %s', o)
                self.print_help()
                sys.exit(1)

        instance = pki.server.instance.PKIServerFactory.create(instance_name)

        if not instance.exists():
            raise Exception('Invalid instance: %s' % instance_name)

        instance.load()

        acme_conf_dir = os.path.join(instance.conf_dir, 'acme')
        realm_conf = os.path.join(acme_conf_dir, 'realm.conf')
        config = {}

        if realm_type:
            # if --type is specified, load the realm.conf template
            source = '/usr/share/pki/acme/realm/{0}/realm.conf'.format(realm_type)
        else:
            # otherwise, load the realm.conf from the instance
            source = realm_conf

        logger.info('Loading %s', source)
        pki.util.load_properties(source, config)

        # if --type or -D is specified, use silent mode
        if realm_type or props:

            logger.info('Setting properties:')
            for name, value in props.items():
                logger.info('- %s: %s', name, value)
                pki.util.set_property(config, name, value)

            instance.store_properties(realm_conf, config)
            return

        # otherwise, use interactive mode

        print('The current value is displayed in the square brackets.')
        print('To keep the current value, simply press Enter.')
        print('To change the current value, enter the new value.')
        print('To remove the current value, enter a blank space.')

        realm_class = config.get('class')

        print()
        print(
            'Enter the type of the realm. '
            'Available types: %s.' % ', '.join(REALM_TYPES.values()))
        realm_type = REALM_TYPES.get(realm_class)
        orig_realm_type = realm_type

        realm_type = pki.util.read_text(
            '  Realm Type',
            options=REALM_TYPES.values(),
            default=realm_type,
            required=True)
        pki.util.set_property(config, 'class', REALM_CLASSES.get(realm_type))

        if orig_realm_type != realm_type:
            source = '/usr/share/pki/acme/realm/{0}/realm.conf'.format(realm_type)
            logger.info('Loading %s', source)
            pki.util.load_properties(source, config)

        if realm_type == 'in-memory':

            print()
            print('Enter the admin username.')
            username = config.get('username')
            username = pki.util.read_text('  Admin Username', default=username, required=True)
            pki.util.set_property(config, 'username', username)

            print()
            print('Enter the admin password.')
            password = config.get('password')
            password = pki.util.read_text(
                '  Admin Password',
                default=password,
                password=True,
                required=True)
            pki.util.set_property(config, 'password', password)

        elif realm_type == 'ds':

            print()
            print('Enter the location of the LDAP server '
                  '(e.g. ldap://localhost.localdomain:389).')
            url = config.get('url')
            url = pki.util.read_text('  Server URL', default=url, required=True)
            pki.util.set_property(config, 'url', url)

            print()
            print('Enter the authentication type. Available types: BasicAuth, SslClientAuth.')
            auth_type = config.get('authType')
            auth_type = pki.util.read_text(
                '  Authentication Type',
                options=['BasicAuth', 'SslClientAuth'],
                default=auth_type,
                required=True)
            pki.util.set_property(config, 'authType', auth_type)

            if auth_type == 'BasicAuth':

                print()
                print('Enter the bind DN.')
                bind_dn = config.get('bindDN')
                bind_dn = pki.util.read_text('  Bind DN', default=bind_dn, required=True)
                pki.util.set_property(config, 'bindDN', bind_dn)

                print()
                print('Enter the bind password.')
                password = config.get('bindPassword')
                password = pki.util.read_text(
                    '  Bind Password',
                    default=password,
                    password=True,
                    required=True)
                pki.util.set_property(config, 'bindPassword', password)

            elif auth_type == 'SslClientAuth':

                print()
                print('Enter the client certificate.')
                nickname = config.get('nickname')
                nickname = pki.util.read_text(
                    '  Client Certificate',
                    default=nickname,
                    required=True)
                pki.util.set_property(config, 'nickname', nickname)

            print()
            print('Enter the subtree DN for the ACME users.')

            users_dn = config.get('usersDN')
            users_dn = pki.util.read_text('  Users DN', default=users_dn, required=True)
            pki.util.set_property(config, 'usersDN', users_dn)

            print()
            print('Enter the subtree DN for the ACME groups.')

            groups_dn = config.get('groupsDN')
            groups_dn = pki.util.read_text('  Groups DN', default=groups_dn, required=True)
            pki.util.set_property(config, 'groupsDN', groups_dn)

        elif realm_type == 'postgresql':

            print()
            print('Enter the location of the PostgreSQL realm '
                  '(e.g. jdbc:postgresql://localhost.localdomain:5432/acme).')
            url = config.get('url')
            url = pki.util.read_text('  Server URL', default=url, required=True)
            pki.util.set_property(config, 'url', url)

            print()
            print('Enter the username for basic authentication.')
            username = config.get('user')
            username = pki.util.read_text('  Username', default=username, required=True)
            pki.util.set_property(config, 'user', username)

            print()
            print('Enter the password for basic authentication.')
            password = config.get('password')
            password = pki.util.read_text(
                '  Password', default=password, password=True, required=True)
            pki.util.set_property(config, 'password', password)

        instance.store_properties(realm_conf, config)
