package org.mediawiki.sparql.mwontop.utils;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import it.unibz.inf.ontop.answering.reformulation.unfolding.QueryUnfolder;
import it.unibz.inf.ontop.injection.OntopModelSettings;
import it.unibz.inf.ontop.iq.IntermediateQuery;
import it.unibz.inf.ontop.iq.exception.EmptyQueryException;
import it.unibz.inf.ontop.iq.impl.IntermediateQueryImpl;
import it.unibz.inf.ontop.iq.impl.QueryTreeComponent;
import it.unibz.inf.ontop.iq.impl.tree.DefaultQueryTreeComponent;
import it.unibz.inf.ontop.iq.impl.tree.DefaultTree;
import it.unibz.inf.ontop.iq.node.IntensionalDataNode;
import it.unibz.inf.ontop.iq.node.QueryNode;
import it.unibz.inf.ontop.iq.node.impl.ConstructionNodeImpl;
import it.unibz.inf.ontop.iq.optimizer.TrueNodesRemovalOptimizer;
import it.unibz.inf.ontop.iq.proposal.QueryMergingProposal;
import it.unibz.inf.ontop.iq.proposal.impl.QueryMergingProposalImpl;
import it.unibz.inf.ontop.iq.tools.RootConstructionNodeEnforcer;
import it.unibz.inf.ontop.model.term.GroundFunctionalTerm;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.NonGroundFunctionalTerm;
import it.unibz.inf.ontop.model.term.impl.ValueConstantImpl;
import it.unibz.inf.ontop.spec.mapping.Mapping;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

class SiteSpecificUnfolder implements QueryUnfolder {
    private final Mapping mapping;
    private final RootConstructionNodeEnforcer rootCnEnforcer;

    @AssistedInject
    private SiteSpecificUnfolder( @Assisted Mapping mapping, RootConstructionNodeEnforcer rootCnEnforcer ) {
        this.mapping = mapping;
        this.rootCnEnforcer = rootCnEnforcer;
    }

    @NonNull
    @Override
    public IntermediateQuery optimize( IntermediateQuery query ) throws EmptyQueryException {
        IntermediateQuery newQuery = null;
        String cachedSite = null;

        Optional<IntensionalDataNode> optionalCurrentIntentionalNode = query.getIntensionalNodes().findFirst();
        while ( optionalCurrentIntentionalNode.isPresent() ) {

            IntensionalDataNode intentionalNode = optionalCurrentIntentionalNode.get();

            Optional<IntermediateQuery> optionalMappingAssertion = mapping.getDefinition(
                    intentionalNode.getProjectionAtom().getPredicate() );

            if ( optionalMappingAssertion.isPresent() ) {
                Optional<String> domainTerm = intentionalNode.getProjectionAtom().getArguments().stream()
                        .filter( GroundFunctionalTerm.class::isInstance )
                        .map( GroundFunctionalTerm.class::cast )
                        .map( t -> t.getTerm( 0 ).toString() )
                        .filter( t -> t.contains( "/wiki/mw" ) )
                        .findFirst();

                if ( domainTerm.isPresent() ) {
                    String siteLink = domainTerm.get();
                    if ( !siteLink.equals( cachedSite ) ) {
                        IntermediateQuery modelQuery = optionalMappingAssertion.get();
                        final Set<QueryNode> constructionNodesForSite = getApplicableMappings( siteLink, modelQuery );
                        if ( !constructionNodesForSite.isEmpty() ) {
                            QueryNode rootNode = modelQuery.getRootNode();
                            if ( constructionNodesForSite.size() == 1 ) {
                                rootNode = constructionNodesForSite.iterator().next();
                            }

                            DefaultTree root = new DefaultTree( rootNode ) {
                            };
                            QueryTreeComponent tree = new DefaultQueryTreeComponent( root ) {
                                {
                                    if ( constructionNodesForSite.size() == 1 ) {
                                        QueryNode join = modelQuery.getChildren( this.getRootNode() ).get( 0 );
                                        this.addChild( this.getRootNode(), join, Optional.empty(), false );
                                        for ( QueryNode ext : modelQuery.getChildren( join ) ) {
                                            this.addChild( join, ext, Optional.empty(), false );
                                        }
                                    } else {
                                        for ( QueryNode constr : constructionNodesForSite ) {
                                            this.addChild( this.getRootNode(), constr, Optional.empty(), false );
                                            QueryNode join = modelQuery.getChildren( constr ).get( 0 );
                                            this.addChild( constr, join, Optional.empty(), false );
                                            for ( QueryNode ext : modelQuery.getChildren( join ) ) {
                                                this.addChild( join, ext, Optional.empty(), false );
                                            }
                                        }
                                    }
                                }
                            };

                            newQuery = new IntermediateQueryImpl(
                                    modelQuery.getDBMetadata(),
                                    modelQuery.getProjectionAtom(),
                                    tree,
                                    modelQuery.getExecutorRegistry(),
                                    null,
                                    new OntopModelSettings() {
                                        @Override
                                        public CardinalityPreservationMode getCardinalityPreservationMode() {
                                            return null;
                                        }

                                        public boolean isTestModeEnabled() {
                                            return false;
                                        }

                                        @Override
                                        public Optional<String> getProperty( String s ) {
                                            return Optional.empty();
                                        }

                                        @Override
                                        public boolean contains( Object o ) {
                                            return false;
                                        }
                                    },
                                    modelQuery.getFactory()

                            );
                            cachedSite = siteLink;
                        }
                    }
                    optionalMappingAssertion = Optional.ofNullable( newQuery );
                }
            }

            query = rootCnEnforcer.enforceRootCn( query );
            QueryMergingProposal queryMerging = new QueryMergingProposalImpl( intentionalNode, optionalMappingAssertion );
            query.applyProposal( queryMerging );

            optionalCurrentIntentionalNode = query.getIntensionalNodes().findFirst();
        }

        return new TrueNodesRemovalOptimizer().optimize( query );
    }

    @NonNull
    private HashSet<QueryNode> getApplicableMappings( @NonNull String siteLink, @NonNull IntermediateQuery modelQuery ) {
        HashSet<QueryNode> constructionNodesForSidelines = new HashSet<>();

        ImmutableList<QueryNode> nodesInTopDownOrder = modelQuery.getNodesInTopDownOrder();
        if ( nodesInTopDownOrder != null ) {
            nodesInTopDownOrder.stream()
                    .filter( ConstructionNodeImpl.class::isInstance )
                    .map( ConstructionNodeImpl.class::cast )
                    .filter( node -> filterNode( node, siteLink ) )
                    .filter( node -> modelQuery.getFirstChild( node ).isPresent() )
                    .forEach( constructionNodesForSidelines::add );
        }
        return constructionNodesForSidelines;
    }

    private boolean filterNode( @NonNull ConstructionNodeImpl node, @NonNull String siteLink ) {
        ImmutableSubstitution<ImmutableTerm> substitution = node.getSubstitution();
        if (substitution == null) {
            return false;
        }
        return substitution.getImmutableMap().values().stream()
                .filter( NonGroundFunctionalTerm.class::isInstance )
                .map( NonGroundFunctionalTerm.class::cast )
                .map( v -> v.getTerm( 0 ) )
                .filter( ValueConstantImpl.class::isInstance )
                .map( ValueConstantImpl.class::cast )
                .anyMatch( t -> siteLink.contains( t.getValue() ) );
    }
}