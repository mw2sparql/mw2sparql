package org.mediawiki.sparql.mwontop.utils;

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
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.term.impl.ValueConstantImpl;
import it.unibz.inf.ontop.spec.mapping.Mapping;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;

import java.util.HashSet;
import java.util.Optional;

class SiteSpecificUnfolder implements QueryUnfolder {
    private final Mapping mapping;
    private final RootConstructionNodeEnforcer rootCnEnforcer;

    @AssistedInject
    private SiteSpecificUnfolder(@Assisted Mapping mapping, RootConstructionNodeEnforcer rootCnEnforcer) {
        this.mapping = mapping;
        this.rootCnEnforcer = rootCnEnforcer;
    }

    @Override
    public IntermediateQuery optimize(IntermediateQuery query) throws EmptyQueryException {
        IntermediateQuery newQuery = null;
        String cachedSite = null;

        Optional<IntensionalDataNode> optionalCurrentIntentionalNode = query.getIntensionalNodes().findFirst();
        while (optionalCurrentIntentionalNode.isPresent()) {

            IntensionalDataNode intentionalNode = optionalCurrentIntentionalNode.get();

            Optional<IntermediateQuery> optionalMappingAssertion = mapping.getDefinition(
                    intentionalNode.getProjectionAtom().getPredicate());

            if (optionalMappingAssertion.isPresent()) {
                String siteLink = null;
                for (int i = 0; i < intentionalNode.getProjectionAtom().getArity(); i++) {
                    if (intentionalNode.getProjectionAtom().getTerm(i) instanceof GroundFunctionalTerm) {
                        String firstTermValue = ((GroundFunctionalTerm) intentionalNode.getProjectionAtom().getTerm(i)).getTerm(0).toString();
                        if (firstTermValue.contains("/wiki/mw")) siteLink = firstTermValue;
                    }
                }
                if (siteLink != null) {
                    if (!siteLink.equals(cachedSite)) {
                        IntermediateQuery modelQuery = optionalMappingAssertion.get();
                        HashSet<QueryNode> constructionNodesForSite = getApplicableMappings(siteLink, modelQuery);
                        QueryNode rootNode = modelQuery.getRootNode();
                        if (constructionNodesForSite.size() == 1) rootNode = constructionNodesForSite.iterator().next();

                        DefaultTree root = new DefaultTree(rootNode) {
                        };
                        QueryTreeComponent tree = new DefaultQueryTreeComponent(root) {
                            {
                                if (constructionNodesForSite.size() == 1) {
                                    QueryNode join = modelQuery.getChildren(this.getRootNode()).get(0);
                                    this.addChild(this.getRootNode(), join, Optional.empty(), false);
                                    for (QueryNode ext : modelQuery.getChildren(join)) {
                                        this.addChild(join, ext, Optional.empty(), false);
                                    }
                                } else {
                                    for (QueryNode constr : constructionNodesForSite) {
                                        this.addChild(this.getRootNode(), constr, Optional.empty(), false);
                                        QueryNode join = modelQuery.getChildren(constr).get(0);
                                        this.addChild(constr, join, Optional.empty(), false);
                                        for (QueryNode ext : modelQuery.getChildren(join)) {
                                            this.addChild(join, ext, Optional.empty(), false);
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
                                    public Optional<String> getProperty(String s) {
                                        return Optional.empty();
                                    }

                                    @Override
                                    public boolean contains(Object o) {
                                        return false;
                                    }
                                },
                                modelQuery.getFactory()

                        );
                        cachedSite = siteLink;
                    }
                    optionalMappingAssertion = Optional.of(newQuery);
                }
            }

            query = rootCnEnforcer.enforceRootCn(query);
            QueryMergingProposal queryMerging = new QueryMergingProposalImpl(intentionalNode, optionalMappingAssertion);
            query.applyProposal(queryMerging);

            optionalCurrentIntentionalNode = query.getIntensionalNodes().findFirst();
        }

        return new TrueNodesRemovalOptimizer().optimize(query);
    }

    private HashSet<QueryNode> getApplicableMappings(String siteLink, IntermediateQuery modelQuery) {
        HashSet<QueryNode> constructionNodesForSidelines = new HashSet<>();

        for (QueryNode node : modelQuery.getNodesInTopDownOrder()) {
            if (node instanceof ConstructionNodeImpl) {
                ImmutableSubstitution<ImmutableTerm> subsList = ((ConstructionNodeImpl) node).getSubstitution();
                for (Variable k : subsList.getDomain()) {
                    ImmutableTerm v = subsList.get(k);
                    if (v instanceof NonGroundFunctionalTerm) {
                        ImmutableTerm t = ((NonGroundFunctionalTerm) v).getTerm(0);
                        if (t instanceof ValueConstantImpl) {
                            if (siteLink.contains(((ValueConstantImpl) t).getValue())) {
                                Optional<QueryNode> firstChild = modelQuery.getFirstChild(node);
                                if (firstChild.isPresent())
                                    constructionNodesForSidelines.add(node);
                            }
                        }
                    }
                }
            }
        }
        return constructionNodesForSidelines;
    }
}