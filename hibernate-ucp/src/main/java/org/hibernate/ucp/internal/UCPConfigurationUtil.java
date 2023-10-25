/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.oracleucp.internal;

import java.util.Map;
import java.util.Properties;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.internal.ConnectionProviderInitiator;


/**
 * Utility class to map Hibernate properties to oracle UCP configuration properties.
 * 
 */
public class UCPConfigurationUtil {
	public static final String CONFIG_PREFIX = "hibernate.oracleucp.";

	@SuppressWarnings("rawtypes")
	public static Properties loadConfiguration(Map props) {
		Properties ucpProps = new Properties();
		copyProperty( AvailableSettings.AUTOCOMMIT, props, "autoCommit", ucpProps );

		copyProperty( AvailableSettings.URL, props, "URL", ucpProps );
		copyProperty( AvailableSettings.USER, props, "user", ucpProps );
		copyProperty( AvailableSettings.PASS, props, "password", ucpProps );

		copyIsolationSetting( props, ucpProps );

		for ( Object keyo : props.keySet() ) {
			if ( !(keyo instanceof String) ) {
				continue;
			}
			String key = (String) keyo;
			if ( key.startsWith( CONFIG_PREFIX ) ) {
				ucpProps.setProperty( key.substring( CONFIG_PREFIX.length() ), (String) props.get( key ) );
			}
		}

		return ucpProps ;
	}

	@SuppressWarnings("rawtypes")
	private static void copyProperty(String srcKey, Map src, String dstKey, Properties dst) {
		if ( src.containsKey( srcKey ) ) {
			dst.setProperty( dstKey, (String) src.get( srcKey ) );
		}
	}

	private static void copyIsolationSetting(Map props, Properties ucpProps) {
		final Integer isolation = ConnectionProviderInitiator.extractIsolation( props );
		if ( isolation != null ) {
			ucpProps.put(
					"transactionIsolation",
					ConnectionProviderInitiator.toIsolationConnectionConstantName( isolation )
			);
		}
	}

}

