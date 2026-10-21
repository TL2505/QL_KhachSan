# Admin: newly created rooms appear first

## Source findings and change

- `rooms.created_at` is the existing database creation field. The shared room DAO previously queried `ORDER BY r.id` ascending, so a newly inserted auto-increment ID appeared at the end. The Admin table sliced that response into ten-row pages. The supplied web tree called `window.renderPaginationComponent`, but did not define it; without an injected helper, rooms after the first ten were unreachable.
- The enhanced Admin view retained search/status filters after creation. A newly created room could consequently be filtered out even after a successful insert. Its success message was also cleared by the reload it immediately started.
- Admin now requests `GET /api/v1/rooms?sort=newest` (also supported by `/api/rooms`). The controller requires Admin authorization for this option and whitelists the sort value. SQL uses `ORDER BY r.created_at DESC, r.id DESC`, with `Cache-Control: no-store`. Ordinary `/rooms` callers, including the Staff room map and desktop clients, retain the original ascending-ID order.
- Room responses expose the stored creation date using a hotel-local ISO string. The model uses the project's existing LocalDateTime adapter, retaining compatibility with desktop Room deserialization. The insert obtains its generated ID and exposes it only after a successful commit; the 201 response includes that ID. No schema change or client-supplied creation date is introduced.
- The Admin modules retain newest-first ordering through filtering and pagination. For older running APIs that omit creation dates and ignore the new query parameter, the Admin view uses descending auto-increment IDs, so a newly inserted room is visible immediately after a frontend refresh. This compatibility behavior does not add fabricated rooms or modify Staff ordering; database creation-time ordering takes effect when the updated backend is compiled and restarted.
- After successful creation, search/type/status filters are cleared and the table returns to page one. The enhanced view checks the fresh server list for the returned ID (or the submitted unique room number for legacy null receipts) before reporting success. A failed reload or a missing new room reports an unverified result rather than pretending the row exists. A removed view ignores late responses.
- Both Admin room modules have local previous/next controls in the existing pagination container when the shared pagination renderer is unavailable. No global pagination implementation, sidebar, stylesheet, booking API or database record was changed for this task.

## Verification

Automated checks use isolated HTTP/JDBC fixtures, never an application database:

- Frontend tests verify true creation-date order ahead of ID, stable ID tie-breaking, missing-date legacy ordering, and nonmutation of received rows.
- Backend tests verify the exact SQL for Admin/default orders; actual timestamp mapping and desktop deserialization; commit/generated-ID behavior and rollback/conflict cases; HTTP ordering options, no-store headers, validation and Admin authorization; and the 201 room ID.
- Chromium checks exercise an 18-row Admin list with no shared pagination helper, page two, active filters, legacy null and modern ID creation receipts, new-room visibility on page one, reload, creation-date ordering, duplicate rejection, failed reloads, missing records, late responses after navigation and unchanged Staff requests.

The cloud workspace cannot access the Windows database or Java PID 14472. These checks are not proof of a room being created on that database.

## Check on the actual Windows application

1. Apply only this room-order change and refresh with Ctrl+F5. Frontend compatibility ordering works even with the old compiled backend, provided its room insertion succeeds.
2. Sign in as an existing Admin. Open room management, navigate to page two and activate a search/type/status filter.
3. Use the normal form to add a real intended room with a unique room number, actual room type and price. Confirm POST `/api/v1/rooms` succeeds. The new backend returns HTTP 201 and `data.id`; legacy servers may return success without data.
4. Confirm a fresh GET `/api/v1/rooms?sort=newest` runs, filters clear, page one is selected, and that exact room is the first row. Navigate to page two/back, then reload and repeat.
5. In the SQL client connected to the same active database, verify the intended room and real order without modifying records:

   ```sql
   SELECT DATABASE() AS active_database;
   SELECT id, room_number, created_at FROM rooms
   ORDER BY created_at DESC, id DESC LIMIT 20;
   ```

6. Open Staff's room map and confirm its existing order/navigation still behaves normally. Verify that a duplicate room number produces a conflict and never reports another successful creation.

To activate the SQL ordering and richer 201 receipt, rebuild/restart the backend using the project's normal launcher and the existing database/key settings. The Windows launcher was previously blocked by missing original AES configuration; do not generate replacement vault keys or initialize a sample database for this room-list task. The frontend compatibility behavior does not resolve that separate booking/startup issue.
