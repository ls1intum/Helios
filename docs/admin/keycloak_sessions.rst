=====================================
Keycloak Session Lifespans (Re-login)
=====================================


.. contents:: Content of this document
    :local:
    :depth: 2


Symptom
-------

Users have to log in to Helios too frequently — typically after a few hours, and reliably after an
overnight gap.


Root cause
----------

The running ``helios`` realm caps sessions at **8 hours**: both ``ssoSessionIdleTimeout`` and
``ssoSessionMaxLifespan`` were ``28800`` seconds. Once either is hit, the Keycloak SSO session ends
and the client (which keeps tokens in memory and re-establishes via ``check-sso``) is forced back
through GitHub login. The access-token lifespan (5 min) and the client-side silent refresh are
correct and are **not** the cause.


Why the earlier fix had no effect
---------------------------------

An earlier change set the correct values (``ssoSessionIdleTimeout=604800`` / 7 days,
``ssoSessionMaxLifespan=2592000`` / 30 days) but never reached the running realm, for two
independent reasons:

1. **Wrong realm file.** It edited ``helios-example-realm.json`` — the **local-dev** realm
   (``helios-example``). Staging and production run the ``helios`` realm from ``helios-realm.json``
   (a server-only file at ``/opt/helios/helios-realm.json``, not in the repository), which was never
   changed.
2. **Import is a no-op on an existing realm.** Both environments start Keycloak with
   ``start-dev --import-realm`` against a **persistent** Postgres Keycloak database. Keycloak's
   start-time import **skips realms that already exist** — it does not overwrite session settings.
   So even editing the correct JSON and redeploying would not change a live realm; realm settings
   live in the database.

Consequently, realm-JSON edits only ever apply to a *fresh* Keycloak database. The dev seed
(``helios-example-realm.json``) already carries the correct values for new local environments.


The fix
-------

Session lifespans must be applied to the **running** realm. This is done with ``kcadm`` (Keycloak's
admin CLI), which updates the live realm in the database — idempotent and safe to re-run.

Automated (on every deploy)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A one-shot ``keycloak-config`` service in ``compose.prod.yaml`` (and ``compose.yaml`` for local dev)
applies the settings after Keycloak starts. It is isolated and non-blocking — it only updates the
three session fields and always exits ``0``, so a slow or unreachable Keycloak never affects the
deploy:

.. code-block:: text

  ssoSessionIdleTimeout   = 604800    # 7 days
  ssoSessionMaxLifespan   = 2592000   # 30 days
  offlineSessionIdleTimeout = 2592000 # 30 days

Because it re-applies on every ``docker compose up``, it also overrides the import-is-a-no-op
problem going forward.

Applying immediately (existing running instance)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To fix a running instance now without waiting for a redeploy, run ``kcadm`` inside the Keycloak
container (the admin credentials are already in its environment as
``KC_BOOTSTRAP_ADMIN_USERNAME`` / ``KC_BOOTSTRAP_ADMIN_PASSWORD``):

.. code-block:: console

  docker exec keycloak bash -c '
    KCADM=/opt/keycloak/bin/kcadm.sh
    "$KCADM" config credentials --server http://localhost:8081 --realm master \
      --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"
    "$KCADM" update realms/helios \
      -s ssoSessionIdleTimeout=604800 \
      -s ssoSessionMaxLifespan=2592000 \
      -s offlineSessionIdleTimeout=2592000
  '

Run it once on each environment's Keycloak (staging and production). New logins immediately receive
the longer session; the client's silent refresh then keeps users signed in for up to 30 days (7
days of inactivity).

.. note::

   The access-token lifespan stays at 5 minutes and "Revoke Refresh Token" stays off, so the
   60-second client refresh and multi-tab usage keep working unchanged.


Verifying
---------

Read the live values back:

.. code-block:: console

  docker exec keycloak bash -c '
    /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8081 --realm master \
      --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1
    /opt/keycloak/bin/kcadm.sh get realms/helios \
      --fields ssoSessionIdleTimeout,ssoSessionMaxLifespan,offlineSessionIdleTimeout
  '

Expect ``604800`` / ``2592000`` / ``2592000``. Behaviourally: leave the app idle overnight and
reopen — no re-login prompt (previously failed at ~8 h).
