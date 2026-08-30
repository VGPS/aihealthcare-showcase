# CG-1: Company Relationship Graph

## Goal
Add a force-directed visual graph at `GET /dashboard/relationships/graph` showing companies
as nodes and relationships (PARTNERSHIP/ACQUISITION/INVESTMENT/COMPETITOR/SUPPLIER/INTEGRATION)
as typed, color-coded edges. Back-end is fully built; this slice is pure visualization.

## What already exists (do NOT rebuild)
- `CompanyRelationship` domain record + `CompanyRelationshipType` enum
- `CompanyRelationshipPort` outbound port + `CompanyRelationshipAdapter` JPA adapter
- `CompanyRelationshipService` keyword detection service
- `CompanyRelationshipController` → `GET /dashboard/relationships` (table view, `relationships.html`)
- `CompanyRelationshipRestController` → `GET /api/v1/relationships` + `POST /detect`

## Files — New
| File | Purpose |
|------|---------|
| `web/dto/VisNode.java` | vis-network node DTO (id, label, value, title) |
| `web/dto/VisEdge.java` | vis-network edge DTO (id, from, to, label, color, value, title) |
| `web/dto/RelationshipGraphResponse.java` | Graph response wrapper (nodes + edges) |
| `templates/relationship-graph.html` | Interactive vis-network graph page |

## Files — Modified
| File | Change |
|------|--------|
| `CompanyRelationshipRestController.java` | Add GET /api/v1/relationships/graph endpoint |
| `CompanyRelationshipController.java` | Add GET /dashboard/relationships/graph handler |
| `relationships.html` | Add Table/Graph tab bar |
| `CompanyRelationshipRestControllerTest.java` | 3 new tests for /graph endpoint |
| `CompanyRelationshipControllerTest.java` | 1 new test for /graph page handler |

## Edge Colors
PARTNERSHIP=#3B82F6 (blue), ACQUISITION=#EF4444 (red), COMPETITOR=#F97316 (orange),
INVESTMENT=#22C55E (green), SUPPLIER=#6B7280 (gray), INTEGRATION=#A855F7 (purple)

## Library
vis-network@9.1.9 via unpkg CDN (standalone UMD build) — no pom.xml changes needed
