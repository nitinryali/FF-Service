package com.ffproject.flagservice.migration;

import com.ffproject.flagservice.flag.Flag;
import com.ffproject.flagservice.tenant.Tenant;
import com.ffproject.flagservice.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent migration from the single-tenant schema to the
 * tenant-scoped schema.
 *
 * <p>Existing flags have no {@code tenantId}, {@code version}, audit fields, or
 * {@code deletedAt}. This runner assigns them to a {@code default} tenant and
 * fills those fields so the new queries (which always filter by tenant) can see
 * them. It also drops the old globally-unique {@code name_1} index, which would
 * otherwise prevent the same flag name existing in two tenants.</p>
 *
 * <p>Every step checks first, so running the service again is a no-op.</p>
 */
@Component
public class FlagDataMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlagDataMigration.class);

    private static final String DEFAULT_TENANT_NAME = "default";
    private static final String LEGACY_INDEX_NAME = "name_1";
    private static final String MIGRATION_PRINCIPAL = "migration";
    private static final long INITIAL_VERSION = 1L;

    private final MongoTemplate mongoTemplate;
    private final TenantRepository tenantRepository;

    public FlagDataMigration(MongoTemplate mongoTemplate, TenantRepository tenantRepository) {
        this.mongoTemplate = mongoTemplate;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        dropLegacyNameIndex();
        String defaultTenantId = ensureDefaultTenant();
        backfillFlags(defaultTenantId);
    }

    private void dropLegacyNameIndex() {
        for (IndexInfo index : mongoTemplate.indexOps(Flag.class).getIndexInfo()) {
            if (LEGACY_INDEX_NAME.equals(index.getName())) {
                mongoTemplate.indexOps(Flag.class).dropIndex(LEGACY_INDEX_NAME);
                log.info("Dropped legacy unique index '{}'", LEGACY_INDEX_NAME);
                return;
            }
        }
    }

    private String ensureDefaultTenant() {
        return tenantRepository.findByName(DEFAULT_TENANT_NAME)
                .map(Tenant::getId)
                .orElseGet(() -> {
                    Tenant tenant = new Tenant();
                    tenant.setName(DEFAULT_TENANT_NAME);
                    Tenant saved = tenantRepository.save(tenant);
                    log.info("Created default tenant {} for pre-existing flags", saved.getId());
                    return saved.getId();
                });
    }

    private void backfillFlags(String defaultTenantId) {
        long tenantBackfilled = mongoTemplate.updateMulti(
                new Query(Criteria.where("tenantId").exists(false)),
                new Update().set("tenantId", defaultTenantId),
                Flag.class).getModifiedCount();

        long versionBackfilled = mongoTemplate.updateMulti(
                new Query(Criteria.where("version").exists(false)),
                new Update().set("version", INITIAL_VERSION),
                Flag.class).getModifiedCount();

        long deletedAtBackfilled = mongoTemplate.updateMulti(
                new Query(Criteria.where("deletedAt").exists(false)),
                new Update().set("deletedAt", null),
                Flag.class).getModifiedCount();

        long auditBackfilled = mongoTemplate.updateMulti(
                new Query(Criteria.where("createdBy").exists(false)),
                new Update().set("createdBy", MIGRATION_PRINCIPAL).set("updatedBy", MIGRATION_PRINCIPAL),
                Flag.class).getModifiedCount();

        if (tenantBackfilled + versionBackfilled + deletedAtBackfilled + auditBackfilled > 0) {
            log.info("Migrated flags to tenant schema: tenant={}, version={}, deletedAt={}, audit={}",
                    tenantBackfilled, versionBackfilled, deletedAtBackfilled, auditBackfilled);
        }
    }
}
