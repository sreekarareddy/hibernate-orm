/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.criteria;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaParameterExpression;
import org.hibernate.query.criteria.JpaRoot;

import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.domain.contacts.Address;
import org.hibernate.testing.orm.domain.contacts.Contact;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Tuple;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Christian Beikov
 */
@DomainModel(standardModels = StandardDomainModel.CONTACTS)
@SessionFactory
@JiraKey("HHH-17410")
public class CountQueryTests {

	@Test
	public void testBasic(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					final HibernateCriteriaBuilder cb = session.getCriteriaBuilder();
					verifyCount( session, cb.createQuery(
							"select e.id, e.name from Contact e where e.gender is null",
							Tuple.class
					) );
					verifyCount( session, cb.createQuery(
							"select e.id as id, e.name as name from Contact e where e.gender = FEMALE",
							Tuple.class
					) );
				}
		);
	}

	@Test
	public void testFetches(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					final HibernateCriteriaBuilder cb = session.getCriteriaBuilder();
					verifyCount( session, cb.createQuery(
							"select e from Contact e join fetch e.alternativeContact",
							Contact.class
					) );
					verifyCollectionCount( session, cb.createQuery(
							"select e from Contact e left join fetch e.addresses",
							Contact.class
					) );
				}
		);
	}

	@Test
	public void testConstructor(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					final HibernateCriteriaBuilder cb = session.getCriteriaBuilder();
					verifyCount( session, cb.createQuery(
							"select new " + Contact.class.getName() + "(e.id, e.name, e.gender, e.birthDay) from Contact e",
							Tuple.class
					) );
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsRecursiveCtes.class)
	public void testCte(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					final HibernateCriteriaBuilder cb = session.getCriteriaBuilder();
					verifyCount( session, cb.createQuery(
							"with alternativeContacts as (" +
									"select c.alternativeContact alt from Contact c where c.id = 1 " +
									"union all " +
									"select c.alt.alternativeContact alt from alternativeContacts c where c.alt.alternativeContact.id <> 1" +
									")" +
									"select ac from alternativeContacts c join c.alt ac order by ac.id",
							Tuple.class
					) );
				}
		);
	}

	@Test
	public void testValues(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					final HibernateCriteriaBuilder cb = session.getCriteriaBuilder();
					final JpaCriteriaQuery<Tuple> cq = cb.createTupleQuery();
					final JpaRoot<Contact> root = cq.from( Contact.class );
					cq.multiselect(
							root.get( "id" ),
							root.get( "name" )
					);
					cq.where(
							root.get( "gender" ).equalTo( Contact.Gender.FEMALE )
					);
					verifyCount( session, cq );
				}
		);
	}

	@Test
	public void testParameters(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					//tag::criteria-extensions-count-query-example[]
					final HibernateCriteriaBuilder cb = session.getCriteriaBuilder();
					final JpaCriteriaQuery<Tuple> cq = cb.createTupleQuery();
					final JpaRoot<Contact> root = cq.from( Contact.class );
					final JpaParameterExpression<Contact.Gender> parameter = cb.parameter( Contact.Gender.class );
					
					cq.multiselect( root.get( "id" ), root.get( "name" ) );
					cq.where( root.get( "gender" ).equalTo( parameter ) );
					final Long count = session.createQuery( cq.createCountQuery() )
							.setParameter( parameter, Contact.Gender.FEMALE )
							.getSingleResult();
					//end::criteria-extensions-count-query-example[]

					final List<Tuple> resultList = session.createQuery( cq )
							.setParameter( parameter, Contact.Gender.FEMALE )
							.getResultList();
					assertEquals( resultList.size(), count.intValue() );
				}
		);
	}

	@BeforeEach
	public void prepareTestData(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			final Contact contact = new Contact(
					1,
					new Contact.Name( "John", "Doe" ),
					Contact.Gender.MALE,
					LocalDate.of( 1970, 1, 1 )
			);
			final Contact alternativeContact = new Contact(
					2,
					new Contact.Name( "Jane", "Doe" ),
					Contact.Gender.FEMALE,
					LocalDate.of( 1970, 1, 1 )
			);
			final Contact alternativeContact2 = new Contact(
					3,
					new Contact.Name( "Granny", "Doe" ),
					Contact.Gender.FEMALE,
					LocalDate.of( 1970, 1, 1 )
			);
			alternativeContact.setAlternativeContact( alternativeContact2 );
			contact.setAlternativeContact( alternativeContact );
			contact.setAddresses(
					List.of(
							new Address( "Street 1", 1234 ),
							new Address( "Street 2", 5678 )
					)
			);
			session.persist( alternativeContact2 );
			session.persist( alternativeContact );
			session.persist( contact );
			alternativeContact2.setAlternativeContact( contact );

			final Contact c4 = new Contact(
					4,
					new Contact.Name( "C4", "Doe" ),
					Contact.Gender.OTHER,
					LocalDate.of( 1970, 1, 1 )
			);
			final Contact c5 = new Contact(
					5,
					new Contact.Name( "C5", "Doe" ),
					Contact.Gender.OTHER,
					LocalDate.of( 1970, 1, 1 )
			);
			final Contact c6 = new Contact(
					6,
					new Contact.Name( "C6", "Doe" ),
					Contact.Gender.OTHER,
					LocalDate.of( 1970, 1, 1 )
			);
			final Contact c7 = new Contact(
					7,
					new Contact.Name( "C7", "Doe" ),
					Contact.Gender.OTHER,
					LocalDate.of( 1970, 1, 1 )
			);
			final Contact c8 = new Contact(
					8,
					new Contact.Name( "C8", "Doe" ),
					Contact.Gender.OTHER,
					LocalDate.of( 1970, 1, 1 )
			);
			c4.setAlternativeContact( c5 );
			c5.setAlternativeContact( c6 );
			c7.setAlternativeContact( c8 );

			session.persist( c6 );
			session.persist( c5 );
			session.persist( c4 );
			session.persist( c8 );
			session.persist( c7 );
		} );
	}

	private <T> void verifyCount(SessionImplementor session, JpaCriteriaQuery<?> query) {
		final List<?> resultList = session.createQuery( query ).getResultList();
		final Long count = session.createQuery( query.createCountQuery() ).getSingleResult();
		assertEquals( resultList.size(), count.intValue() );
	}

	private <T> void verifyCollectionCount(SessionImplementor session, JpaCriteriaQuery<Contact> query) {
		final List<Contact> resultList = session.createQuery( query ).getResultList();
		final Long count = session.createQuery( query.createCountQuery() ).getSingleResult();
		int ormSize = 0;
		for ( Contact contact : resultList ) {
			ormSize++;
			ormSize += Math.max( contact.getAddresses().size() - 1, 0 );
			ormSize += Math.max( contact.getPhoneNumbers().size() - 1, 0 );
		}

		assertEquals( ormSize, count.intValue() );
	}

	@AfterEach
	public void dropTestData(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.createMutationQuery( "update Contact set alternativeContact = null" ).executeUpdate();
			session.createMutationQuery( "delete Contact" ).executeUpdate();
		} );
	}
}
