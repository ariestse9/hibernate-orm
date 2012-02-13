/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.internal.source;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.hibernate.AssertionFailure;
import org.hibernate.EntityMode;
import org.hibernate.TruthValue;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.NamingStrategy;
import org.hibernate.cfg.NotYetImplementedException;
import org.hibernate.cfg.ObjectNameNormalizer;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.beans.BeanInfoHelper;
import org.hibernate.metamodel.internal.source.hbm.Helper;
import org.hibernate.metamodel.spi.binding.AbstractPluralAttributeBinding;
import org.hibernate.metamodel.spi.binding.AttributeBinding;
import org.hibernate.metamodel.spi.binding.AttributeBindingContainer;
import org.hibernate.metamodel.spi.binding.BasicAttributeBinding;
import org.hibernate.metamodel.spi.binding.BasicPluralAttributeElementBinding;
import org.hibernate.metamodel.spi.binding.ComponentAttributeBinding;
import org.hibernate.metamodel.spi.binding.EntityBinding;
import org.hibernate.metamodel.spi.binding.EntityDiscriminator;
import org.hibernate.metamodel.spi.binding.HibernateTypeDescriptor;
import org.hibernate.metamodel.spi.binding.IdGenerator;
import org.hibernate.metamodel.spi.binding.InheritanceType;
import org.hibernate.metamodel.spi.binding.ManyToOneAttributeBinding;
import org.hibernate.metamodel.spi.binding.MetaAttribute;
import org.hibernate.metamodel.spi.binding.PluralAttributeElementNature;
import org.hibernate.metamodel.spi.binding.SimpleValueBinding;
import org.hibernate.metamodel.spi.binding.SingularAssociationAttributeBinding;
import org.hibernate.metamodel.spi.binding.SingularAttributeBinding;
import org.hibernate.metamodel.spi.binding.TypeDefinition;
import org.hibernate.metamodel.spi.domain.Attribute;
import org.hibernate.metamodel.spi.domain.Component;
import org.hibernate.metamodel.spi.domain.Entity;
import org.hibernate.metamodel.spi.domain.PluralAttribute;
import org.hibernate.metamodel.spi.domain.SingularAttribute;
import org.hibernate.metamodel.spi.relational.Column;
import org.hibernate.metamodel.spi.relational.Datatype;
import org.hibernate.metamodel.spi.relational.DerivedValue;
import org.hibernate.metamodel.spi.relational.ForeignKey;
import org.hibernate.metamodel.spi.relational.Identifier;
import org.hibernate.metamodel.spi.relational.Schema;
import org.hibernate.metamodel.spi.relational.SimpleValue;
import org.hibernate.metamodel.spi.relational.Table;
import org.hibernate.metamodel.spi.relational.TableSpecification;
import org.hibernate.metamodel.spi.relational.Tuple;
import org.hibernate.metamodel.spi.relational.UniqueKey;
import org.hibernate.metamodel.spi.relational.Value;
import org.hibernate.metamodel.spi.source.AttributeSource;
import org.hibernate.metamodel.spi.source.AttributeSourceContainer;
import org.hibernate.metamodel.spi.source.BasicPluralAttributeElementSource;
import org.hibernate.metamodel.spi.source.ColumnBindingDefaults;
import org.hibernate.metamodel.spi.source.ColumnSource;
import org.hibernate.metamodel.spi.source.ComponentAttributeSource;
import org.hibernate.metamodel.spi.source.ConstraintSource;
import org.hibernate.metamodel.spi.source.DerivedValueSource;
import org.hibernate.metamodel.spi.source.DiscriminatorSource;
import org.hibernate.metamodel.spi.source.EntityHierarchy;
import org.hibernate.metamodel.spi.source.EntitySource;
import org.hibernate.metamodel.spi.source.ExplicitHibernateTypeSource;
import org.hibernate.metamodel.spi.source.LocalBindingContext;
import org.hibernate.metamodel.spi.source.MappingException;
import org.hibernate.metamodel.spi.source.MetaAttributeContext;
import org.hibernate.metamodel.spi.source.MetaAttributeSource;
import org.hibernate.metamodel.spi.source.MetadataImplementor;
import org.hibernate.metamodel.spi.source.Orderable;
import org.hibernate.metamodel.spi.source.PluralAttributeElementSource;
import org.hibernate.metamodel.spi.source.PluralAttributeNature;
import org.hibernate.metamodel.spi.source.PluralAttributeSource;
import org.hibernate.metamodel.spi.source.RelationalValueSource;
import org.hibernate.metamodel.spi.source.RelationalValueSourceContainer;
import org.hibernate.metamodel.spi.source.RootEntitySource;
import org.hibernate.metamodel.spi.source.SimpleIdentifierSource;
import org.hibernate.metamodel.spi.source.SingularAttributeNature;
import org.hibernate.metamodel.spi.source.SingularAttributeSource;
import org.hibernate.metamodel.spi.source.Sortable;
import org.hibernate.metamodel.spi.source.SubclassEntityContainer;
import org.hibernate.metamodel.spi.source.SubclassEntitySource;
import org.hibernate.metamodel.spi.source.TableSource;
import org.hibernate.metamodel.spi.source.ToOneAttributeSource;
import org.hibernate.metamodel.spi.source.UniqueConstraintSource;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.service.config.spi.ConfigurationService;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.type.Type;

/**
 * The common binder shared between annotations and {@code hbm.xml} processing.
 * <p/>
 * The API consists of {@link #Binder( MetadataImplementor )} and {@link #processEntityHierarchies(Iterable)}
 *
 * @author Steve Ebersole
 * @author Hardy Ferentschik
 */
public class Binder {
	private final MetadataImplementor metadata;
	private final ArrayList<String> processedEntityNames = new ArrayList<String>();

	private InheritanceType currentInheritanceType;
	private EntityMode currentHierarchyEntityMode;
	private LocalBindingContext currentBindingContext;
    private HashMap<String, EntitySource> sourcesByName = new HashMap<String, EntitySource>();

	public Binder( MetadataImplementor metadata ) {
		this.metadata = metadata;
	}

	/**
	 * Process an entity hierarchy.
	 *
	 * @param entityHierarchies THe hierarchies to process.
	 */
	public void processEntityHierarchies( Iterable<? extends EntityHierarchy> entityHierarchies ) {
        // Index sources by name so we can find and resolve entities on the fly as references to them are encountered (e.g., within associations)
        for ( EntityHierarchy hierarchy : entityHierarchies )
            mapSourcesByName( hierarchy.getRootEntitySource() );

	    for ( EntityHierarchy hierarchy : entityHierarchies ) {
    	    currentInheritanceType = hierarchy.getHierarchyInheritanceType();
            RootEntitySource rootEntitySource = hierarchy.getRootEntitySource();
    		EntityBinding rootEntityBinding = createEntityBinding( rootEntitySource, null );
    		// Create identifier generator for root entity
    		Properties properties = new Properties();
            properties.putAll( metadata.getServiceRegistry().getService( ConfigurationService.class ).getSettings() );
            // TODO: where should these be added???
            if ( !properties.contains( AvailableSettings.PREFER_POOLED_VALUES_LO ) )
                properties.put( AvailableSettings.PREFER_POOLED_VALUES_LO, "false" );
            if ( !properties.contains( PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER ) )
                properties.put( PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER, new ObjectNameNormalizer() {

                    @Override
                    protected boolean isUseQuotedIdentifiersGlobally() {
                        return metadata.isGloballyQuotedIdentifiers();
                    }

                    @Override
                    protected NamingStrategy getNamingStrategy() {
                        return metadata.getNamingStrategy();
                    }
                } );
            rootEntityBinding.getHierarchyDetails().getEntityIdentifier().
                createIdentifierGenerator( metadata.getIdentifierGeneratorFactory(), properties );

    		if ( currentInheritanceType != InheritanceType.NO_INHERITANCE )
    		    processHierarchySubEntities( rootEntitySource, rootEntityBinding );
    		currentHierarchyEntityMode = null;
	    }
	}

	private void mapSourcesByName( EntitySource source ) {
        sourcesByName.put( source.getEntityName(), source );
        for ( SubclassEntitySource subsource : source.subclassEntitySources() )
            mapSourcesByName( subsource );
	}

	private void processHierarchySubEntities(SubclassEntityContainer subclassEntitySource, EntityBinding superEntityBinding) {
		for ( SubclassEntitySource subEntity : subclassEntitySource.subclassEntitySources() ) {
			EntityBinding entityBinding = createEntityBinding( subEntity, superEntityBinding );
			processHierarchySubEntities( subEntity, entityBinding );
		}
	}


	// Entities ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private EntityBinding createEntityBinding(EntitySource entitySource, EntityBinding superEntityBinding) {
		if ( processedEntityNames.contains( entitySource.getEntityName() ) ) {
			return metadata.getEntityBinding( entitySource.getEntityName() );
		}

		currentBindingContext = entitySource.getLocalBindingContext();
		try {
			final EntityBinding entityBinding = doCreateEntityBinding( entitySource, superEntityBinding );

			metadata.addEntity( entityBinding );
			processedEntityNames.add( entityBinding.getEntity().getName() );

			processFetchProfiles( entitySource, entityBinding );

			return entityBinding;
		}
		finally {
			currentBindingContext = null;
		}
	}

	private EntityBinding doCreateEntityBinding(EntitySource entitySource, EntityBinding superEntityBinding) {
		final EntityBinding entityBinding = createBasicEntityBinding( entitySource, superEntityBinding );

		bindSecondaryTables( entitySource, entityBinding );
		Deque<TableSpecification> tableStack = new ArrayDeque<TableSpecification>(  );
		bindAttributes( entitySource, entityBinding, tableStack );

		bindTableUniqueConstraints( entitySource, entityBinding );

		return entityBinding;
	}

	private EntityBinding createBasicEntityBinding(EntitySource entitySource, EntityBinding superEntityBinding) {
		if ( superEntityBinding == null ) {
			return makeRootEntityBinding( (RootEntitySource) entitySource );
		}
		else {
			switch ( currentInheritanceType ) {
				case SINGLE_TABLE:
					return makeDiscriminatedSubclassBinding( (SubclassEntitySource) entitySource, superEntityBinding );
				case JOINED:
					return makeJoinedSubclassBinding( (SubclassEntitySource) entitySource, superEntityBinding );
				case TABLE_PER_CLASS:
					return makeUnionedSubclassBinding( (SubclassEntitySource) entitySource, superEntityBinding );
				default:
					// extreme internal error!
					throw new AssertionFailure( "Internal condition failure" );
			}
		}
	}

	private EntityBinding makeRootEntityBinding(RootEntitySource entitySource) {
		currentHierarchyEntityMode = entitySource.getEntityMode();

		final EntityBinding entityBinding = buildBasicEntityBinding( entitySource, null );

		bindPrimaryTable( entitySource, entityBinding );

		bindIdentifier( entitySource, entityBinding );
		bindVersion( entityBinding, entitySource );
		bindDiscriminator( entitySource, entityBinding );

		entityBinding.getHierarchyDetails().setCaching( entitySource.getCaching() );
		entityBinding.getHierarchyDetails().setExplicitPolymorphism( entitySource.isExplicitPolymorphism() );
		entityBinding.getHierarchyDetails().setOptimisticLockStyle( entitySource.getOptimisticLockStyle() );

		entityBinding.setMutable( entitySource.isMutable() );
		entityBinding.setWhereFilter( entitySource.getWhere() );
		entityBinding.setRowId( entitySource.getRowId() );

		return entityBinding;
	}

	private EntityBinding buildBasicEntityBinding(EntitySource entitySource, EntityBinding superEntityBinding) {
		final EntityBinding entityBinding = superEntityBinding == null
				? new EntityBinding( currentInheritanceType, currentHierarchyEntityMode )
				: new EntityBinding( superEntityBinding );

		final String entityName = entitySource.getEntityName();
		final String className = currentHierarchyEntityMode == EntityMode.POJO ? entitySource.getClassName() : null;

		final Entity entity = new Entity(
				entityName,
				className,
				currentBindingContext.makeClassReference( className ),
				superEntityBinding == null ? null : superEntityBinding.getEntity()
		);
		entityBinding.setEntity( entity );

		entityBinding.setJpaEntityName( entitySource.getJpaEntityName() );

		if ( currentHierarchyEntityMode == EntityMode.POJO ) {
			final String proxy = entitySource.getProxy();
			if ( proxy != null ) {
				entityBinding.setProxyInterfaceType(
						currentBindingContext.makeClassReference(
								currentBindingContext.qualifyClassName( proxy )
						)
				);
				entityBinding.setLazy( true );
			}
			else if ( entitySource.isLazy() ) {
				entityBinding.setProxyInterfaceType( entityBinding.getEntity().getClassReferenceUnresolved() );
				entityBinding.setLazy( true );
			}
		}
		else {
			entityBinding.setProxyInterfaceType( null );
			entityBinding.setLazy( entitySource.isLazy() );
		}

		final String customTuplizerClassName = entitySource.getCustomTuplizerClassName();
		if ( customTuplizerClassName != null ) {
			entityBinding.setCustomEntityTuplizerClass(
					currentBindingContext.<EntityTuplizer>locateClassByName(
							customTuplizerClassName
					)
			);
		}

		final String customPersisterClassName = entitySource.getCustomPersisterClassName();
		if ( customPersisterClassName != null ) {
			entityBinding.setCustomEntityPersisterClass(
					currentBindingContext.<EntityPersister>locateClassByName(
							customPersisterClassName
					)
			);
		}

		entityBinding.setMetaAttributeContext( buildMetaAttributeContext( entitySource ) );

		entityBinding.setDynamicUpdate( entitySource.isDynamicUpdate() );
		entityBinding.setDynamicInsert( entitySource.isDynamicInsert() );
		entityBinding.setBatchSize( entitySource.getBatchSize() );
		entityBinding.setSelectBeforeUpdate( entitySource.isSelectBeforeUpdate() );
		entityBinding.setAbstract( entitySource.isAbstract() );

		entityBinding.setCustomLoaderName( entitySource.getCustomLoaderName() );
		entityBinding.setCustomInsert( entitySource.getCustomSqlInsert() );
		entityBinding.setCustomUpdate( entitySource.getCustomSqlUpdate() );
		entityBinding.setCustomDelete( entitySource.getCustomSqlDelete() );

		if ( entitySource.getSynchronizedTableNames() != null ) {
			entityBinding.addSynchronizedTableNames( entitySource.getSynchronizedTableNames() );
		}

		entityBinding.setJpaCallbackClasses(entitySource.getJpaCallbackClasses());

		return entityBinding;
	}

	private EntityBinding makeDiscriminatedSubclassBinding(SubclassEntitySource entitySource, EntityBinding superEntityBinding) {
		final EntityBinding entityBinding = buildBasicEntityBinding( entitySource, superEntityBinding );

		entityBinding.setPrimaryTable( superEntityBinding.getPrimaryTable() );
		entityBinding.setPrimaryTableName( superEntityBinding.getPrimaryTableName() );
		bindDiscriminatorValue( entitySource, entityBinding );

		return entityBinding;
	}

	private EntityBinding makeJoinedSubclassBinding(SubclassEntitySource entitySource, EntityBinding superEntityBinding) {
		final EntityBinding entityBinding = buildBasicEntityBinding( entitySource, superEntityBinding );

		bindPrimaryTable( entitySource, entityBinding );

		// todo : join

		return entityBinding;
	}

	private EntityBinding makeUnionedSubclassBinding(SubclassEntitySource entitySource, EntityBinding superEntityBinding) {
		final EntityBinding entityBinding = buildBasicEntityBinding( entitySource, superEntityBinding );

		bindPrimaryTable( entitySource, entityBinding );

		// todo : ??

		return entityBinding;
	}

	// Attributes ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private void bindIdentifier(RootEntitySource entitySource, EntityBinding entityBinding) {
		if ( entitySource.getIdentifierSource() == null ) {
			throw new AssertionFailure( "Expecting identifier information on root entity descriptor" );
		}
		switch ( entitySource.getIdentifierSource().getNature() ) {
			case SIMPLE: {
				bindSimpleIdentifier( (SimpleIdentifierSource) entitySource.getIdentifierSource(), entityBinding );
				break;
			}
			case AGGREGATED_COMPOSITE: {
				// composite id with an actual component class
			    break;
			}
			case COMPOSITE: {
				// what we used to term an "embedded composite identifier", which is not tobe confused with the JPA
				// term embedded. Specifically a composite id where there is no component class, though there may
				// be a @IdClass :/
			    break;
			}
		}
	}

	private void bindSimpleIdentifier(
			SimpleIdentifierSource identifierSource,
			EntityBinding entityBinding) {
		final BasicAttributeBinding idAttributeBinding = doBasicSingularAttributeBindingCreation(
				identifierSource.getIdentifierAttributeSource(), entityBinding
		);

		entityBinding.getHierarchyDetails().getEntityIdentifier().setValueBinding( idAttributeBinding );
		IdGenerator generator = identifierSource.getIdentifierGeneratorDescriptor();
		if ( generator == null ) {
			Map<String, String> params = new HashMap<String, String>();
			params.put( IdentifierGenerator.ENTITY_NAME, entityBinding.getEntity().getName() );
			generator = new IdGenerator( "default_assign_identity_generator", "assigned", params );
		}
		entityBinding.getHierarchyDetails()
				.getEntityIdentifier()
				.setIdGenerator( generator );

		final Value relationalValue = idAttributeBinding.getValue();

		if ( SimpleValue.class.isInstance( relationalValue ) ) {
			if ( !Column.class.isInstance( relationalValue ) ) {
				// this should never ever happen..
				throw new AssertionFailure( "Simple-id was not a column." );
			}
			entityBinding.getPrimaryTable().getPrimaryKey().addColumn( Column.class.cast( relationalValue ) );
		}
		else {
			for ( SimpleValue subValue : ( (Tuple) relationalValue ).values() ) {
				if ( Column.class.isInstance( subValue ) ) {
					entityBinding.getPrimaryTable().getPrimaryKey().addColumn( Column.class.cast( subValue ) );
				}
			}
		}
	}

	private void bindVersion(EntityBinding entityBinding, RootEntitySource entitySource) {
		final SingularAttributeSource versioningAttributeSource = entitySource.getVersioningAttributeSource();
		if ( versioningAttributeSource == null ) {
			return;
		}

		BasicAttributeBinding attributeBinding = doBasicSingularAttributeBindingCreation(
				versioningAttributeSource, entityBinding
		);
		entityBinding.getHierarchyDetails().setVersioningAttributeBinding( attributeBinding );
	}

	public static final ColumnBindingDefaults DISCRIMINATOR_COLUMN_BINDING_DEFAULTS = new ColumnBindingDefaults() {
		@Override
		public boolean areValuesIncludedInInsertByDefault() {
			return true;
		}

		@Override
		public boolean areValuesIncludedInUpdateByDefault() {
			return false;
		}

		@Override
		public boolean areValuesNullableByDefault() {
			return false;
		}
	};

	private void bindDiscriminator(RootEntitySource entitySource, EntityBinding entityBinding) {
		final DiscriminatorSource discriminatorSource = entitySource.getDiscriminatorSource();
		if ( discriminatorSource == null ) {
			return;
		}

		EntityDiscriminator discriminator = new EntityDiscriminator();
		SimpleValue relationalValue = makeSimpleValue(
				entityBinding,
				discriminatorSource.getDiscriminatorRelationalValueSource(),
				DISCRIMINATOR_COLUMN_BINDING_DEFAULTS
		);
		discriminator.setBoundValue( relationalValue );

		discriminator.getExplicitHibernateTypeDescriptor().setExplicitTypeName(
				discriminatorSource.getExplicitHibernateTypeName() != null
						? discriminatorSource.getExplicitHibernateTypeName()
						: "string"
		);

		discriminator.setInserted( discriminatorSource.isInserted() );
		discriminator.setForced( discriminatorSource.isForced() );

		entityBinding.getHierarchyDetails().setEntityDiscriminator( discriminator );
		entityBinding.setDiscriminatorMatchValue( entitySource.getDiscriminatorMatchValue() );
		resolveTypeInformation(discriminator.getExplicitHibernateTypeDescriptor(), relationalValue);
	}

	private void bindDiscriminatorValue(SubclassEntitySource entitySource, EntityBinding entityBinding) {
		final String discriminatorValue = entitySource.getDiscriminatorMatchValue();
		if ( discriminatorValue == null ) {
			return;
		}
		entityBinding.setDiscriminatorMatchValue( discriminatorValue );
	}

	private void bindAttributes(
			AttributeSourceContainer attributeSourceContainer,
			AttributeBindingContainer attributeBindingContainer,
			Deque<TableSpecification> tableStack) {
		// todo : we really need the notion of a Stack here for the table from which the columns come for binding these attributes.
		// todo : adding the concept (interface) of a source of attribute metadata would allow reuse of this method for entity, component, unique-key, etc
		// for now, simply assume all columns come from the base table....

		for ( AttributeSource attributeSource : attributeSourceContainer.attributeSources() ) {
			if ( attributeSource.isSingular() ) {
				final SingularAttributeSource singularAttributeSource = (SingularAttributeSource) attributeSource;
				if ( singularAttributeSource.getNature() == SingularAttributeNature.COMPONENT ) {
					bindComponent( (ComponentAttributeSource) singularAttributeSource, attributeBindingContainer, tableStack );
				}
				else {
					doBasicSingularAttributeBindingCreation( singularAttributeSource, attributeBindingContainer );
				}
			}
			else {
				bindPersistentCollection( (PluralAttributeSource) attributeSource, attributeBindingContainer, tableStack );
			}
		}
	}

	private void bindComponent(
			ComponentAttributeSource attributeSource,
			AttributeBindingContainer container,
			Deque<TableSpecification> tableStack) {
		final String attributeName = attributeSource.getName();
		SingularAttribute attribute = container.getAttributeContainer().locateComponentAttribute( attributeName );
		if ( attribute == null ) {
			final Component component = new Component(
					attributeSource.getPath(),
					attributeSource.getClassName(),
					attributeSource.getClassReference(),
					null // component inheritance not YET supported
			);
			attribute = container.getAttributeContainer().createComponentAttribute( attributeName, component );
		}
		ComponentAttributeBinding componentAttributeBinding = container.makeComponentAttributeBinding( attribute );

		if ( StringHelper.isNotEmpty( attributeSource.getParentReferenceAttributeName() ) ) {
			final SingularAttribute parentReferenceAttribute =
					componentAttributeBinding.getComponent()
							.createSingularAttribute( attributeSource.getParentReferenceAttributeName() );
			componentAttributeBinding.setParentReference( parentReferenceAttribute );
		}

		componentAttributeBinding.setMetaAttributeContext(
				buildMetaAttributeContext( attributeSource.metaAttributes(), container.getMetaAttributeContext() )
		);

		bindAttributes( attributeSource, componentAttributeBinding, tableStack );
	}

	private void bindPersistentCollection(
			PluralAttributeSource attributeSource,
			AttributeBindingContainer attributeBindingContainer,
			Deque<TableSpecification> tableStack) {
		final PluralAttribute existingAttribute = attributeBindingContainer.getAttributeContainer()
				.locatePluralAttribute( attributeSource.getName() );
		final AbstractPluralAttributeBinding pluralAttributeBinding;

		if ( attributeSource.getPluralAttributeNature() == PluralAttributeNature.BAG ) {
			final PluralAttribute attribute = existingAttribute != null
					? existingAttribute
					: attributeBindingContainer.getAttributeContainer().createBag( attributeSource.getName() );
			pluralAttributeBinding = attributeBindingContainer.makeBagAttributeBinding(
					attribute,
					convert( attributeSource.getElementSource().getNature() )
			);
		}
		else if ( attributeSource.getPluralAttributeNature() == PluralAttributeNature.SET ) {
			final PluralAttribute attribute = existingAttribute != null
					? existingAttribute
					: attributeBindingContainer.getAttributeContainer().createSet( attributeSource.getName() );
			pluralAttributeBinding = attributeBindingContainer.makeSetAttributeBinding(
					attribute,
					convert( attributeSource.getElementSource().getNature() )
			);
		}
		else {
			// todo : implement other collection types
			throw new NotYetImplementedException( "Collections other than bag and set not yet implemented :(" );
		}

		doBasicPluralAttributeBinding( attributeSource, pluralAttributeBinding );

		bindCollectionTable( attributeSource, pluralAttributeBinding );
		bindSortingAndOrdering( attributeSource, pluralAttributeBinding );

		bindCollectionKey( attributeSource, pluralAttributeBinding, tableStack );
		bindCollectionElement( attributeSource, pluralAttributeBinding );
		bindCollectionIndex( attributeSource, pluralAttributeBinding );

		metadata.addCollection( pluralAttributeBinding );
	}

	private void doBasicPluralAttributeBinding(PluralAttributeSource source, AbstractPluralAttributeBinding binding) {
		binding.setFetchTiming( source.getFetchTiming() );
		binding.setFetchStyle( source.getFetchStyle() );

		binding.setCaching( source.getCaching() );

		binding.getHibernateTypeDescriptor().setJavaTypeName(
				source.getPluralAttributeNature().reportedJavaType().getName()
		);
		binding.getHibernateTypeDescriptor().setExplicitTypeName( source.getTypeInformation().getName() );
		binding.getHibernateTypeDescriptor().getTypeParameters().putAll( source.getTypeInformation().getParameters() );

		if ( StringHelper.isNotEmpty( source.getCustomPersisterClassName() ) ) {
			binding.setCollectionPersisterClass(
					currentBindingContext.<CollectionPersister>locateClassByName( source.getCustomPersisterClassName() )
			);
		}

		if ( source.getCustomPersisterClassName() != null ) {
			binding.setCollectionPersisterClass(
					metadata.<CollectionPersister>locateClassByName( source.getCustomPersisterClassName() )
			);
		}

		binding.setCustomLoaderName( source.getCustomLoaderName() );
		binding.setCustomSqlInsert( source.getCustomSqlInsert() );
		binding.setCustomSqlUpdate( source.getCustomSqlUpdate() );
		binding.setCustomSqlDelete( source.getCustomSqlDelete() );
		binding.setCustomSqlDeleteAll( source.getCustomSqlDeleteAll() );

		binding.setMetaAttributeContext(
				buildMetaAttributeContext(
						source.metaAttributes(),
						binding.getContainer().getMetaAttributeContext()
				)
		);

		doBasicAttributeBinding( source, binding );
	}

//	private CollectionLaziness interpretLaziness(String laziness) {
//		if ( laziness == null ) {
//			laziness = Boolean.toString( metadata.getMappingDefaults().areAssociationsLazy() );
//		}
//
//		if ( "extra".equals( laziness ) ) {
//			return CollectionLaziness.EXTRA;
//		}
//		else if ( "false".equals( laziness ) ) {
//			return CollectionLaziness.NOT;
//		}
//		else if ( "true".equals( laziness ) ) {
//			return CollectionLaziness.LAZY;
//		}
//
//		throw new MappingException(
//				String.format( "Unexpected collection laziness value %s", laziness ),
//				currentBindingContext.getOrigin()
//		);
//	}

	private void bindCollectionTable(
			PluralAttributeSource attributeSource,
			AbstractPluralAttributeBinding pluralAttributeBinding) {
		if ( attributeSource.getElementSource().getNature() == org.hibernate.metamodel.spi.source.PluralAttributeElementNature.ONE_TO_MANY ) {
			return;
		}

		final Schema.Name schemaName = Helper.determineDatabaseSchemaName(
				attributeSource.getExplicitSchemaName(),
				attributeSource.getExplicitCatalogName(),
				currentBindingContext
		);
		final Schema schema = metadata.getDatabase().locateSchema( schemaName );

		final String tableName = attributeSource.getExplicitCollectionTableName();
		if ( StringHelper.isNotEmpty( tableName ) ) {
			final Identifier tableIdentifier = Identifier.toIdentifier(
					currentBindingContext.getNamingStrategy().tableName( tableName )
			);
			Table collectionTable = schema.locateTable( tableIdentifier );
			if ( collectionTable == null ) {
				collectionTable = schema.createTable( tableIdentifier );
			}
			pluralAttributeBinding.setCollectionTable( collectionTable );
		}
		else {
			// todo : not sure wel have all the needed info here in all cases, specifically when needing to know the "other side"
			final EntityBinding owner = pluralAttributeBinding.getContainer().seekEntityBinding();
			final String ownerTableLogicalName = Table.class.isInstance( owner.getPrimaryTable() )
					? Table.class.cast( owner.getPrimaryTable() ).getTableName().getName()
					: null;
			String collectionTableName = currentBindingContext.getNamingStrategy().collectionTableName(
					owner.getEntity().getName(),
					ownerTableLogicalName,
					null,	// todo : here
					null,	// todo : and here
					pluralAttributeBinding.getContainer().getPathBase() + '.' + attributeSource.getName()
			);
			collectionTableName = quoteIdentifier( collectionTableName );
			pluralAttributeBinding.setCollectionTable(
					schema.locateOrCreateTable(
							Identifier.toIdentifier(
									collectionTableName
							)
					)
			);
		}

		if ( StringHelper.isNotEmpty( attributeSource.getCollectionTableComment() ) ) {
			pluralAttributeBinding.getCollectionTable().addComment( attributeSource.getCollectionTableComment() );
		}

		if ( StringHelper.isNotEmpty( attributeSource.getCollectionTableCheck() ) ) {
			pluralAttributeBinding.getCollectionTable().addCheckConstraint( attributeSource.getCollectionTableCheck() );
		}

		pluralAttributeBinding.setWhere( attributeSource.getWhere() );
	}

	private void bindCollectionKey(
		PluralAttributeSource attributeSource,
		AbstractPluralAttributeBinding pluralAttributeBinding,
		Deque<TableSpecification> tableStack) {

//		TableSpecification targetTable = tableStack.peekLast();
		pluralAttributeBinding.getPluralAttributeKeyBinding().prepareForeignKey(
				attributeSource.getKeySource().getExplicitForeignKeyName(),
				//tableStack.peekLast().getLogicalName()
				null  // todo : handle secondary table names
		);
		pluralAttributeBinding.getPluralAttributeKeyBinding().getForeignKey().setDeleteRule(
				attributeSource.getKeySource().getOnDeleteAction()
		);
		final ForeignKey foreignKey = pluralAttributeBinding.getPluralAttributeKeyBinding().getForeignKey();

		Iterator<SimpleValueBinding> targetValueBindings = null;
		if ( attributeSource.getKeySource().getReferencedEntityAttributeName() != null ) {
			final EntityBinding ownerEntityBinding = pluralAttributeBinding.getContainer().seekEntityBinding();
			final AttributeBinding referencedAttributeBinding =
					ownerEntityBinding
							.locateAttributeBinding( attributeSource.getKeySource().getReferencedEntityAttributeName() );
			if ( ! referencedAttributeBinding.getAttribute().isSingular() ) {
				throw new MappingException(
						String.format(
								"Collection (%s) property-ref is a plural attribute (%s); must be singular.",
								pluralAttributeBinding.getAttribute().getRole(),
								referencedAttributeBinding
						),
						currentBindingContext.getOrigin()
				);
			}
			targetValueBindings = ( ( SingularAttributeBinding) referencedAttributeBinding ).getSimpleValueBindings().iterator();
		}
		for ( RelationalValueSource valueSource : attributeSource.getKeySource().getValueSources() ) {
			SimpleValue targetValue = null;
			if ( targetValueBindings != null ) {
				if ( ! targetValueBindings.hasNext() ) {
					throw new MappingException(
							String.format(
									"More collection key source columns than target columns for collection: %s",
									pluralAttributeBinding.getAttribute().getRole()
							),
							currentBindingContext.getOrigin()
					);
				}
				targetValue = targetValueBindings.next().getSimpleValue();
			}
			if ( ColumnSource.class.isInstance( valueSource ) ) {
				final ColumnSource columnSource = ColumnSource.class.cast( valueSource );
				final Column column = makeColumn( columnSource, COLL_KEY_COLUMN_BINDING_DEFAULTS, pluralAttributeBinding.getCollectionTable() );
				if ( targetValue != null && ! Column.class.isInstance( targetValue ) ) {
					throw new MappingException(
							String.format(
									"Type mismatch between collection key source and target; collection: %s; source column (%s) corresponds with target derived value (%s).",
									pluralAttributeBinding.getAttribute().getRole(),
									columnSource.getName(),
									DerivedValue.class.cast( targetValue ).getExpression()
							),
							currentBindingContext.getOrigin()
					);
				}
				foreignKey.addColumnMapping( column, Column.class.cast( targetValue ) );
			}
			else {
				// TODO: deal with formulas???
			}
		}
		if ( targetValueBindings != null && targetValueBindings.hasNext() ) {
			throw new MappingException(
					String.format(
							"More collection key target columns than source columns for collection: %s",
							pluralAttributeBinding.getAttribute().getRole()
					),
					currentBindingContext.getOrigin()
			);
		}
	}

	private static final ColumnBindingDefaults COLL_KEY_COLUMN_BINDING_DEFAULTS = new ColumnBindingDefaults() {
		@Override
		public boolean areValuesIncludedInInsertByDefault() {
			return true;
		}

		@Override
		public boolean areValuesIncludedInUpdateByDefault() {
			return false;
		}

		@Override
		public boolean areValuesNullableByDefault() {
			return false;
		}
	};

	private void bindCollectionElement(
			PluralAttributeSource attributeSource,
			AbstractPluralAttributeBinding pluralAttributeBinding) {
		final PluralAttributeElementSource elementSource = attributeSource.getElementSource();
		if ( elementSource.getNature() == org.hibernate.metamodel.spi.source.PluralAttributeElementNature.BASIC ) {
			final BasicPluralAttributeElementSource basicElementSource = (BasicPluralAttributeElementSource) elementSource;
			final BasicPluralAttributeElementBinding basicCollectionElement = (BasicPluralAttributeElementBinding) pluralAttributeBinding.getPluralAttributeElementBinding();
			resolveTypeInformation(
					basicElementSource.getExplicitHibernateTypeSource(),
					pluralAttributeBinding.getAttribute(),
					basicCollectionElement
			);
			bindBasicPluralElementRelationalValues(
					basicElementSource,
					basicCollectionElement
			);
			return;
		}

// todo : handle cascades
//		final Cascadeable cascadeable = (Cascadeable) binding.getPluralAttributeElementBinding();
//		cascadeable.setCascadeStyles( source.getCascadeStyles() );

		// todo : implement
		throw new NotYetImplementedException(
				String.format(
						"Support for collection elements of type %s not yet implemented",
						elementSource.getNature()
				)
		);
	}

	private void bindBasicPluralElementRelationalValues(
			RelationalValueSourceContainer relationalValueSourceContainer,
			BasicPluralAttributeElementBinding elementBinding) {
		elementBinding.setSimpleValueBindings(
				createSimpleRelationalValues(
						relationalValueSourceContainer,
						elementBinding.getPluralAttributeBinding().getContainer(),
						elementBinding.getPluralAttributeBinding().getAttribute()
				)
		);
	}

	private void bindCollectionIndex(
			PluralAttributeSource attributeSource,
			AbstractPluralAttributeBinding pluralAttributeBinding) {
		if ( attributeSource.getPluralAttributeNature() != PluralAttributeNature.LIST
				&& attributeSource.getPluralAttributeNature() != PluralAttributeNature.MAP ) {
			return;
		}

		// todo : implement
		throw new NotYetImplementedException();
	}

	private void bindSortingAndOrdering(
			PluralAttributeSource attributeSource,
			AbstractPluralAttributeBinding pluralAttributeBinding) {
		if ( Sortable.class.isInstance( attributeSource ) ) {
			final Sortable sortable = Sortable.class.cast( attributeSource );
			if ( sortable.isSorted() ) {
				// todo : handle setting comparator

				// and then return because sorting and ordering are mutually exclusive
				return;
			}
		}

		if ( Orderable.class.isInstance( attributeSource ) ) {
			final Orderable orderable = Orderable.class.cast( attributeSource );
			if ( orderable.isOrdered() ) {
				// todo : handle setting ordering
			}
		}
	}

	private void doBasicAttributeBinding(AttributeSource attributeSource, AttributeBinding attributeBinding) {
		attributeBinding.setPropertyAccessorName( attributeSource.getPropertyAccessorName() );
		attributeBinding.setIncludedInOptimisticLocking( attributeSource.isIncludedInOptimisticLocking() );
	}

	private PluralAttributeElementNature convert(org.hibernate.metamodel.spi.source.PluralAttributeElementNature pluralAttributeElementNature) {
		return PluralAttributeElementNature.valueOf( pluralAttributeElementNature.name() );
	}

	private BasicAttributeBinding doBasicSingularAttributeBindingCreation(
			SingularAttributeSource attributeSource,
			AttributeBindingContainer attributeBindingContainer) {
		final SingularAttribute existingAttribute = attributeBindingContainer.getAttributeContainer()
				.locateSingularAttribute( attributeSource.getName() );
		final SingularAttribute attribute;
		if ( existingAttribute != null ) {
			attribute = existingAttribute;
		}
		else if ( attributeSource.isVirtualAttribute() ) {
			attribute = attributeBindingContainer.getAttributeContainer().createVirtualSingularAttribute(
					attributeSource.getName()
			);
		}
		else {
			attribute = attributeBindingContainer.getAttributeContainer()
					.createSingularAttribute( attributeSource.getName() );
		}

		final BasicAttributeBinding attributeBinding;
		if ( attributeSource.getNature() == SingularAttributeNature.BASIC )
			attributeBinding = attributeBindingContainer.makeBasicAttributeBinding( attribute );
		else if ( attributeSource.getNature() == SingularAttributeNature.MANY_TO_ONE )
			attributeBinding = attributeBindingContainer.makeManyToOneAttributeBinding( attribute );
		else throw new NotYetImplementedException();

		attributeBinding.setGeneration( attributeSource.getGeneration() );
		attributeBinding.setLazy( attributeSource.isLazy() );
		attributeBinding.setIncludedInOptimisticLocking( attributeSource.isIncludedInOptimisticLocking() );

		attributeBinding.setPropertyAccessorName(
				Helper.getPropertyAccessorName(
						attributeSource.getPropertyAccessorName(),
						false,
						currentBindingContext.getMappingDefaults().getPropertyAccessorName()
				)
		);

		bindRelationalValues( attributeSource, attributeBinding );

        resolveTypeInformation( attributeSource.getTypeInformation(), attributeBinding );
        if ( attributeSource.getNature() == SingularAttributeNature.MANY_TO_ONE )
            resolveToOneInformation( ( ToOneAttributeSource ) attributeSource, ( ManyToOneAttributeBinding ) attributeBinding );

        if ( attributeBinding instanceof SingularAssociationAttributeBinding ) {
            SingularAssociationAttributeBinding assocAttrBinding = ( SingularAssociationAttributeBinding ) attributeBinding;
            String referencedEntityName = assocAttrBinding.getReferencedEntityName();
            EntityBinding referencedEntityBinding = getEntityBinding( referencedEntityName );
            if (referencedEntityBinding == null) {
                EntitySource source = sourcesByName.get(referencedEntityName);
                createEntityBinding(source, referencedEntityBinding);
            }
            AttributeBinding referencedAttrBinding = assocAttrBinding.isPropertyReference() ?
                referencedEntityBinding.locateAttributeBinding( assocAttrBinding.getReferencedAttributeName() ) :
                referencedEntityBinding.getHierarchyDetails().getEntityIdentifier().getValueBinding();
            assocAttrBinding.resolveReference( referencedAttrBinding );
            referencedAttrBinding.addEntityReferencingAttributeBinding( assocAttrBinding );
        }

		attributeBinding.setMetaAttributeContext(
				buildMetaAttributeContext(
						attributeSource.metaAttributes(),
						attributeBindingContainer.getMetaAttributeContext()
				)
		);

		return attributeBinding;
	}

	private EntityBinding getEntityBinding( String entityName ) {
	    // Check if binding has already been created
        EntityBinding binding = metadata.getEntityBinding( entityName );
        if ( binding == null ) {
            // Find appropriate source to create binding
            EntitySource source = sourcesByName.get( entityName );
            // Get super entity binding (creating it if necessary using recursive call to this method)
            EntityBinding superBinding = source instanceof SubclassEntitySource
                ? getEntityBinding( ( ( SubclassEntitySource ) source ).superclassEntitySource().getEntityName() )
                : null;
            // Create entity binding
            binding = createEntityBinding( source, superBinding );
            // Create entity binding's sub-entity bindings
            processHierarchySubEntities( source, binding );
        }
        return binding;
	}

	private void resolveTypeInformation(ExplicitHibernateTypeSource typeSource, BasicAttributeBinding attributeBinding) {
		final Class<?> attributeJavaType = determineJavaType( attributeBinding.getAttribute() );
		if ( attributeJavaType != null ) {
			attributeBinding.getAttribute()
					.resolveType( currentBindingContext.makeJavaType( attributeJavaType.getName() ) );
		}

		resolveTypeInformation( typeSource, attributeBinding.getHibernateTypeDescriptor(), attributeJavaType );
		resolveTypeInformation( attributeBinding.getHibernateTypeDescriptor(), (SimpleValue)attributeBinding.getValue() );
	}

	private void resolveTypeInformation(
			ExplicitHibernateTypeSource typeSource,
			PluralAttribute attribute,
			BasicPluralAttributeElementBinding collectionElement) {
		final Class<?> attributeJavaType = determineJavaType( attribute );
		resolveTypeInformation( typeSource, collectionElement.getHibernateTypeDescriptor(), attributeJavaType );
	}

	private void resolveTypeInformation(
			ExplicitHibernateTypeSource typeSource,
			HibernateTypeDescriptor hibernateTypeDescriptor,
			Class<?> discoveredJavaType) {
		if ( discoveredJavaType != null ) {
			hibernateTypeDescriptor.setJavaTypeName( discoveredJavaType.getName() );
		}

		final String explicitTypeName = typeSource.getName();
		if ( explicitTypeName != null ) {
			final TypeDefinition typeDefinition = currentBindingContext.getMetadataImplementor()
					.getTypeDefinition( explicitTypeName );
			if ( typeDefinition != null ) {
				hibernateTypeDescriptor.setExplicitTypeName( typeDefinition.getTypeImplementorClass().getName() );
				hibernateTypeDescriptor.getTypeParameters().putAll( typeDefinition.getParameters() );
			}
			else {
				hibernateTypeDescriptor.setExplicitTypeName( explicitTypeName );
			}
			final Map<String, String> parameters = typeSource.getParameters();
			if ( parameters != null ) {
				hibernateTypeDescriptor.getTypeParameters().putAll( parameters );
			}
		}
		else {
			if ( discoveredJavaType == null ) {
				// we will have problems later determining the Hibernate Type to use.  Should we throw an
				// exception now?  Might be better to get better contextual info
			}
		}
	}

    private void resolveTypeInformation( HibernateTypeDescriptor descriptor,
                                         SimpleValue value ) {
        Type resolvedType = descriptor.getResolvedTypeMapping();
        if ( resolvedType == null ) {
            final String name = descriptor.getExplicitTypeName() == null ? descriptor.getJavaTypeName() : descriptor.getExplicitTypeName();
            if (name != null) {
                resolvedType = metadata.getTypeResolver().heuristicType( name, convertTypeParametersToProperties( descriptor ) );
                descriptor.setResolvedTypeMapping( resolvedType );
            }
            if (resolvedType == null) return;
        }
        if ( descriptor.getJavaTypeName() == null ) descriptor.setJavaTypeName( resolvedType.getReturnedClass().getName() );
        if ( value.getDatatype() == null )
            value.setDatatype( new Datatype( resolvedType.sqlTypes( metadata )[0], resolvedType.getName(),
                                             resolvedType.getReturnedClass() ) );
    }

    private Properties convertTypeParametersToProperties( HibernateTypeDescriptor descriptor ) {
        Properties props = new Properties( );
        if ( descriptor.getTypeParameters() != null ) props.putAll( descriptor.getTypeParameters() );
        return props;
    }

	/**
	 * @param attribute the domain attribute
	 *
	 * @return Returns the Java type of the attribute using reflection or {@code null} if the type cannot be discovered
	 */
	private Class<?> determineJavaType(final SingularAttribute attribute) {
		try {
			final Class<?> ownerClass = attribute.getAttributeContainer().getClassReference();
			return ReflectHelper.reflectedPropertyClass( ownerClass, attribute.getName() );
		}
		catch ( Exception ignore ) {
			// todo : log it?
		}
		return null;
	}

	private Class<?> determineJavaType(PluralAttribute attribute) {
		try {
			final Class<?> ownerClass = attribute.getAttributeContainer().getClassReference();
			PluralAttributeJavaTypeDeterminerDelegate delegate = new PluralAttributeJavaTypeDeterminerDelegate(
					ownerClass,
					attribute.getName()
			);
			BeanInfoHelper.visitBeanInfo( ownerClass, delegate );
			return delegate.javaType;
		}
		catch ( Exception ignore ) {
			// todo : log it?
		}
		return null;
	}

	private void resolveToOneInformation(ToOneAttributeSource attributeSource, ManyToOneAttributeBinding attributeBinding) {
		final String referencedEntityName = attributeSource.getReferencedEntityName() != null
				? attributeSource.getReferencedEntityName()
				: attributeBinding.getAttribute().getSingularAttributeType().getClassName();
		attributeBinding.setReferencedEntityName( referencedEntityName );
		// todo : we should consider basing references on columns instead of property-ref, which would require a resolution (later) of property-ref to column names
		attributeBinding.setReferencedAttributeName( attributeSource.getReferencedEntityAttributeName() );

		attributeBinding.setCascadeStyles( attributeSource.getCascadeStyles() );
		attributeBinding.setFetchTiming( attributeSource.getFetchTiming() );
		attributeBinding.setFetchStyle( attributeSource.getFetchStyle() );
	}

	private MetaAttributeContext buildMetaAttributeContext(EntitySource entitySource) {
		return buildMetaAttributeContext(
				entitySource.metaAttributes(),
				true,
				currentBindingContext.getMetadataImplementor().getGlobalMetaAttributeContext()
		);
	}

	private static MetaAttributeContext buildMetaAttributeContext(
			Iterable<MetaAttributeSource> metaAttributeSources,
			MetaAttributeContext parentContext) {
		return buildMetaAttributeContext( metaAttributeSources, false, parentContext );
	}

	private static MetaAttributeContext buildMetaAttributeContext(
			Iterable<MetaAttributeSource> metaAttributeSources,
			boolean onlyInheritable,
			MetaAttributeContext parentContext) {
		final MetaAttributeContext subContext = new MetaAttributeContext( parentContext );

		for ( MetaAttributeSource metaAttributeSource : metaAttributeSources ) {
			if ( onlyInheritable & !metaAttributeSource.isInheritable() ) {
				continue;
			}

			final String name = metaAttributeSource.getName();
			final MetaAttribute inheritedMetaAttribute = parentContext.getMetaAttribute( name );
			MetaAttribute metaAttribute = subContext.getLocalMetaAttribute( name );
			if ( metaAttribute == null || metaAttribute == inheritedMetaAttribute ) {
				metaAttribute = new MetaAttribute( name );
				subContext.add( metaAttribute );
			}
			metaAttribute.addValue( metaAttributeSource.getValue() );
		}

		return subContext;
	}

	// Relational ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private void bindPrimaryTable(EntitySource entitySource, EntityBinding entityBinding) {
		final TableSource tableSource = entitySource.getPrimaryTable();
		final Table table = createTable( entityBinding, tableSource );
		entityBinding.setPrimaryTable( table );
		entityBinding.setPrimaryTableName( table.getTableName().getName() );
	}

	private void bindSecondaryTables(EntitySource entitySource, EntityBinding entityBinding) {
		for ( TableSource secondaryTableSource : entitySource.getSecondaryTables() ) {
			final Table table = createTable( entityBinding, secondaryTableSource );
			entityBinding.addSecondaryTable( secondaryTableSource.getLogicalName(), table );
		}
	}

	private Table createTable(EntityBinding entityBinding, TableSource tableSource) {
		String tableName = tableSource.getExplicitTableName();
		if ( StringHelper.isEmpty( tableName ) ) {
			tableName = currentBindingContext.getNamingStrategy()
					.classToTableName( entityBinding.getEntity().getClassName() );
		}
		else {
			tableName = currentBindingContext.getNamingStrategy().tableName( tableName );
		}
		tableName = quoteIdentifier( tableName );

		final Schema.Name databaseSchemaName = Helper.determineDatabaseSchemaName(
				tableSource.getExplicitSchemaName(),
				tableSource.getExplicitCatalogName(),
				currentBindingContext
		);
		return currentBindingContext.getMetadataImplementor()
				.getDatabase()
				.locateSchema( databaseSchemaName )
				.locateOrCreateTable( Identifier.toIdentifier( tableName ) );
	}

	private void bindTableUniqueConstraints(EntitySource entitySource, EntityBinding entityBinding) {
		for ( ConstraintSource constraintSource : entitySource.getConstraints() ) {
			if ( constraintSource instanceof UniqueConstraintSource ) {
				TableSpecification table = entityBinding.locateTable( constraintSource.getTableName() );
				if ( table == null ) {
					// throw exception !?
				}
				String constraintName = constraintSource.name();
				if ( constraintName == null ) {
					// create a default name
				}

				UniqueKey uniqueKey = table.getOrCreateUniqueKey( constraintName );
				for ( String columnName : constraintSource.columnNames() ) {
					uniqueKey.addColumn( table.locateOrCreateColumn( quoteIdentifier( columnName ) ) );
				}
			}
		}
	}

	private void bindRelationalValues(
			RelationalValueSourceContainer relationalValueSourceContainer,
			SingularAttributeBinding attributeBinding) {
		attributeBinding.setSimpleValueBindings(
				createSimpleRelationalValues(
						relationalValueSourceContainer,
						attributeBinding.getContainer(),
						attributeBinding.getAttribute()
				)
		);
	}

	private List<SimpleValueBinding> createSimpleRelationalValues(
			RelationalValueSourceContainer relationalValueSourceContainer,
			AttributeBindingContainer attributeBindingContainer,
			Attribute attribute) {

		List<SimpleValueBinding> valueBindings = new ArrayList<SimpleValueBinding>();

		if ( !relationalValueSourceContainer.relationalValueSources().isEmpty() ) {
			for ( RelationalValueSource valueSource : relationalValueSourceContainer.relationalValueSources() ) {
				final TableSpecification table = attributeBindingContainer
						.seekEntityBinding()
						.locateTable( valueSource.getContainingTableName() );

				if ( ColumnSource.class.isInstance( valueSource ) ) {
					final ColumnSource columnSource = ColumnSource.class.cast( valueSource );
					final Column column = makeColumn( (ColumnSource) valueSource, relationalValueSourceContainer, table );
					valueBindings.add(
							new SimpleValueBinding(
									column,
									decode( columnSource.isIncludedInInsert(), relationalValueSourceContainer.areValuesIncludedInInsertByDefault() ),
									decode(
											columnSource.isIncludedInUpdate(),
											relationalValueSourceContainer.areValuesIncludedInUpdateByDefault()
									)
							)
					);
				}
				else {
					valueBindings.add(
							new SimpleValueBinding(
									makeDerivedValue( ( (DerivedValueSource) valueSource ), table )
							)
					);
				}
			}
		}
		else {
			String name = metadata.getOptions()
					.getNamingStrategy()
					.propertyToColumnName( attribute.getName() );
			name = quoteIdentifier( name );
			Column column = attributeBindingContainer
									.seekEntityBinding()
									.getPrimaryTable()
									.locateOrCreateColumn( name );
			column.setNullable( relationalValueSourceContainer.areValuesNullableByDefault() );
			valueBindings.add( new SimpleValueBinding( column ) );
		}
		return valueBindings;
	}

	private String quoteIdentifier(String identifier) {
		return currentBindingContext.isGloballyQuotedIdentifiers() ? StringHelper.quote( identifier ) : identifier;
	}

	private SimpleValue makeSimpleValue(
			EntityBinding entityBinding,
			RelationalValueSource valueSource,
			ColumnBindingDefaults columnBindingDefaults) {
		final TableSpecification table = entityBinding.locateTable( valueSource.getContainingTableName() );

		if ( ColumnSource.class.isInstance( valueSource ) ) {
			return makeColumn( (ColumnSource) valueSource, columnBindingDefaults, table );
		}
		else {
			return makeDerivedValue( (DerivedValueSource) valueSource, table );
		}
	}

	private Column makeColumn(
			ColumnSource columnSource,
			ColumnBindingDefaults columnBindingDefaults,
			TableSpecification table) {
		String name = columnSource.getName();
		name = metadata.getOptions().getNamingStrategy().columnName( name );
		name = quoteIdentifier( name );
		final Column column = table.locateOrCreateColumn( name );
		column.setNullable(
				decode( columnSource.isNullable(), columnBindingDefaults.areValuesNullableByDefault() )
		);
		column.setDefaultValue( columnSource.getDefaultValue() );
		column.setSqlType( columnSource.getSqlType() );
		column.setSize( columnSource.getSize() );
		column.setDatatype( columnSource.getDatatype() );
		column.setReadFragment( columnSource.getReadFragment() );
		column.setWriteFragment( columnSource.getWriteFragment() );
		column.setUnique( columnSource.isUnique() );
		column.setCheckCondition( columnSource.getCheckCondition() );
		column.setComment( columnSource.getComment() );
		return column;
	}

	private boolean decode(TruthValue truthValue, boolean defaultValue) {
		switch ( truthValue ) {
			case FALSE: {
				return false;
			}
			case TRUE: {
				return true;
			}
			default: {
				return defaultValue;
			}
		}
	}

	private DerivedValue makeDerivedValue(DerivedValueSource derivedValueSource, TableSpecification table) {
		return table.locateOrCreateDerivedValue( derivedValueSource.getExpression() );
	}

	private void processFetchProfiles(EntitySource entitySource, EntityBinding entityBinding) {
		// todo : process the entity-local fetch-profile declaration
	}

	private class PluralAttributeJavaTypeDeterminerDelegate implements BeanInfoHelper.BeanInfoDelegate {
		private final Class<?> ownerClass;
		private final String attributeName;
		private Class<?> javaType = null;

		private PluralAttributeJavaTypeDeterminerDelegate(Class<?> ownerClass, String attributeName) {
			this.ownerClass = ownerClass;
			this.attributeName = attributeName;
		}

		@Override
		public void processBeanInfo(BeanInfo beanInfo) throws Exception {
			java.lang.reflect.Type collectionAttributeType = null;
			if ( beanInfo.getPropertyDescriptors() == null || beanInfo.getPropertyDescriptors().length == 0 ) {
				// we need to look for the field and look at it...
				collectionAttributeType = ownerClass.getField( attributeName ).getGenericType();
			}
			else {
				for ( PropertyDescriptor propertyDescriptor : beanInfo.getPropertyDescriptors() ) {
					if ( propertyDescriptor.getName().equals( attributeName ) ) {
						if ( propertyDescriptor.getReadMethod() != null ) {
							collectionAttributeType = propertyDescriptor.getReadMethod().getGenericReturnType();
						}
						else if ( propertyDescriptor.getWriteMethod() != null ) {
							collectionAttributeType = propertyDescriptor.getWriteMethod().getGenericParameterTypes()[0];
						}
					}
				}
			}
			if ( collectionAttributeType != null ) {
				if ( ParameterizedType.class.isInstance( collectionAttributeType ) ) {
					final java.lang.reflect.Type[] types = ( (ParameterizedType) collectionAttributeType ).getActualTypeArguments();
					if ( types == null ) {
						return;
					}
					else if ( types.length == 1 ) {
						javaType = (Class<?>) types[0];
					}
					else if ( types.length == 2 ) {
						// Map<K,V>
						javaType = (Class<?>) types[1];
					}
				}
				else {
					javaType = (Class<?>) collectionAttributeType;
				}
			}
		}

	}

}