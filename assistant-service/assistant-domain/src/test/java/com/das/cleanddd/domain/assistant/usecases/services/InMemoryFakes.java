package com.das.cleanddd.domain.assistant.usecases.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.entities.OrderDraft;
import com.das.cleanddd.domain.assistant.entities.OrderDraftId;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;
import com.das.cleanddd.domain.assistant.ports.ICatalogPort;
import com.das.cleanddd.domain.assistant.ports.ProductView;

/** Hand-written fakes for the domain ports, so the tests read as the rules they check. */
final class InMemoryFakes {

    private InMemoryFakes() {
    }

    static final class Drafts implements IOrderDraftRepository {
        final Map<OrderDraftId, OrderDraft> stored = new HashMap<>();

        @Override
        public void save(OrderDraft draft) {
            stored.put(draft.id(), draft);
        }

        @Override
        public Optional<OrderDraft> findById(OrderDraftId id) {
            return Optional.ofNullable(stored.get(id));
        }
    }

    static final class AuditTrail implements IAgentAuditTrail {
        final List<AgentAuditEntry> entries = new ArrayList<>();

        @Override
        public void append(AgentAuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<AgentAuditEntry> forConversation(String conversationId) {
            return entries.stream().filter(e -> e.conversationId().equals(conversationId)).toList();
        }
    }

    static final class Catalog implements ICatalogPort {
        final Map<String, ProductView> products = new HashMap<>();
        final Map<String, Integer> stock = new HashMap<>();

        Catalog with(ProductView product, int available) {
            products.put(product.id(), product);
            stock.put(product.id(), available);
            return this;
        }

        @Override
        public List<ProductView> findProductsByName(String name, int page) {
            return products.values().stream().filter(p -> p.name().contains(name)).toList();
        }

        @Override
        public Optional<ProductView> findProduct(String productId) {
            return Optional.ofNullable(products.get(productId));
        }

        @Override
        public boolean isAvailable(String productId, int quantity) {
            return stock.getOrDefault(productId, 0) >= quantity;
        }
    }

    static ProductView product(String id, String name, String gtin, boolean active, Boolean coldChain) {
        return new ProductView(id, name, "box", gtin, active, false,
                new ProductView.Storage(null, null, null, false, coldChain, null));
    }
}
