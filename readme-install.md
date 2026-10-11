run commands backend
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"


run commands for docker
docker compose down -v
docker compose up -d postgres


db connection


Field	Value
Host name/address	localhost
Port	5432
Maintenance database	fleet
Username	fleet
Password	1d1e68881b40357fc9b6f48fd6b3ce2f

Fix the DB
Fix in pgAdmin

In the left panel, right-click your server (e.g. "Fleet Portal (local)") → Properties…
If the crash happened in the Register dialog, open the Register dialog again and use the same tab there.
Open the Parameters tab.
Click + to add a row: Name GSS encryption mode, Value disable.
Click + again: Name SSL mode, Value disable. The local Docker database doesn't use SSL, so this skips another step that isn't needed.
Save, then double-click the server to connect.

queries to be run 

-- All requests with dealer, model and SLA status
select r.request_number, d.name as dealer, m.name as model, r.quantity,
       r.allocated_qty as from_stock, r.production_required as to_produce,
       r.status, r.sla_due_at, r.created_at
from fleet_requests r
join dealers d on d.id = r.dealer_id
join vehicle_variants v on v.id = r.variant_id
join vehicle_models m on m.id = v.model_id
order by r.created_at desc;

-- How many requests are in each stage
select status, count(*) from fleet_requests group by status order by count(*) desc;

-- Full workflow history of the seeded request
select h.created_at, h.action, h.from_status, h.to_status, h.actor_role, h.reason
from workflow_history h join fleet_requests r on r.id = h.fleet_request_id
where r.request_number = 'FLT-2026-0891'
order by h.created_at;

-- Quotations with their GST breakdown
select q.quotation_number, r.request_number, q.status, q.base_amount, q.gst_rate,
       q.gst_amount, q.total_amount, q.payment_status, q.pdf_sha256
from quotations q join fleet_requests r on r.id = q.fleet_request_id
order by q.created_at desc;

-- Payment receipts
select receipt_number, receipt_type, transaction_reference, amount, status, received_date
from payment_receipts order by created_at desc;

-- Stock by location
select l.name as location, v.name as variant, s.quantity_available, s.quantity_allocated, l.active
from inventory_stock s
join inventory_locations l on l.id = s.location_id
join vehicle_variants v on v.id = s.variant_id
order by l.name;

-- Latest audit log entries
select created_at, actor_username, action, entity_type, entity_id, reason
from audit_logs order by id desc limit 50;

-- Users and roles (password hashes are BCrypt, never plain text)
select u.username, u.full_name, r.code as role, u.active
from users u join roles r on r.id = u.role_id;



Main tables

Table	What it holds
fleet_requests	The requests and their current state
workflow_history	Every state change, plus SLA breach events
audit_logs	Who did what, with before/after JSON
quotations, quotation_line_items	Quotations; issued ones carry a frozen snapshot
payment_receipts	Deposits and payments
inventory_stock, stock_allocations	Yard stock and the units reserved for each request
production_orders, dispatches	Operations scheduling
dealers, regions, plants, vehicle_models, vehicle_variants, tax_rates, sla_policies	Master data
users, roles, permissions, refresh_tokens	Accounts and access
flyway_schema_history	Which migrations have run
