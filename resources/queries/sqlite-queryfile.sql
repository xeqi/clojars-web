--name: find-user
SELECT *
FROM users
WHERE user = :username
LIMIT 1;

--name: find-user-by-user-or-email
SELECT *
FROM users
WHERE (
  user = :username_or_email
  OR
  email = :username_or_email
)
LIMIT 1;

--name: find-user-by-password-reset-code
SELECT *
FROM users
WHERE (
      password_reset_code = :reset_code
      AND
      password_reset_code_created_at >= :reset_code_created_at
)
LIMIT 1;

--name: find-groupnames
SELECT name
FROM groups
WHERE user = :username;

--name: group-membernames
SELECT user
FROM groups
WHERE name = :groupname;

--name: group-keys
SELECT users.pgp_key
FROM groups
INNER JOIN users
ON users.user = groups.user
WHERE groups.name = :groupname;

--name: jars-by-username
SELECT j.*
FROM jars j
JOIN (
  SELECT group_name, jar_name, max(created) AS created
  from jars
  group by group_name, jar_name
) l
ON (
   j.group_name = l.group_name
   AND
   j.jar_name = l.jar_name
   AND
   j.created = l.created
)
WHERE j.user = :username
ORDER BY j.group_name asc, j.jar_name asc;

--name: jars-by-groupname
SELECT j.*
FROM jars j
JOIN (
  SELECT  jar_name, max(created) AS created
  from jars
  group by group_name, jar_name
) l
ON (
   j.jar_name = l.jar_name
   AND
   j.created = l.created
)
WHERE j.group_name = :groupname
order by j.group_name asc, j.jar_name asc;

--name: recent-versions
SELECT DISTINCT(version)
FROM jars
WHERE (
  group_name = :groupname
  and
  jar_name = :jarname
)
ORDER BY created desc;

--name: recent-versions-limit
SELECT DISTINCT(version)
FROM jars
WHERE (
  group_name = :groupname
  and
  jar_name = :jarname
)
ORDER BY created desc
LIMIT :num;

--name: count-versions
SELECT count(DISTINCT version) as count
FROM jars
WHERE (
  group_name = :groupname
  and
  jar_name = :jarname
);

--name: recent-jars
select j.* 
from jars j
join (
  select group_name, jar_name, max(created) as created
  from jars
  group by group_name, jar_name
) l
on (
  j.group_name = l.group_name
  and
  j.jar_name = l.jar_name
  and
  j.created = l.created
)
order by l.created desc
limit 6;

--name: jar-exists
select exists(
  select 1
  from jars
  where (
    group_name = :groupname
    and
    jar_name = :jarname
  )
);

--name: find-jar
SELECT *
FROM jars
WHERE (
  group_name = :groupname
  and
  jar_name = :jarname
)
ORDER BY version LIKE '%-SNAPSHOT' asc, created desc
LIMIT 1;

--name: find-jar-versioned
SELECT *
FROM jars
WHERE (
  group_name = :groupname
  and
  jar_name = :jarname
  and
  version = :version
)
ORDER BY created desc
LIMIT 1;

--name: all-projects
SELECT DISTINCT group_name, jar_name
FROM jars
ORDER BY group_name asc, jar_name asc
LIMIT :num
OFFSET :offset;

--name: count-all-projects
SELECT COUNT(*) as count
FROM (
  SELECT DISTINCT group_name, jar_name
  FROM jars
);

--name: count-projects-before
SELECT COUNT(*) as count
FROM (
  SELECT DISTINCT group_name, jar_name
  FROM jars
  ORDER BY group_name, jar_name
)
WHERE group_name || '/' || jar_name < :s;

--name: insert-user!
INSERT INTO 'users' (email, user, password, pgp_key, created, ssh_key, salt)
VALUES (:email, :username, :password, :pgp_key, :created, '', '');

--name: insert-group!
INSERT INTO 'groups' (name, user)
VALUES (:groupname, :username);

--name: update-user!
UPDATE users
SET email = :email, user = :username, pgp_key = :pgp_key, password = :password
WHERE user = :account;

--name: update-user-password!
UPDATE users
SET password = :password, password_reset_code = null, password_reset_code_created_at = null
WHERE password_reset_code = :reset_code;

--name: set-password-reset-code!
UPDATE users
set password_reset_code = :reset_code, password_reset_code_created_at = :reset_code_created_at
WHERE (
  user = :username_or_email
  OR
  email = :username_or_email
);

--name: find-groups-jars-information
select j.jar_name, j.group_name, homepage, description, user,
j.version as latest_version, r2.version as latest_release
from jars j
-- Find the latest version
join
(select jar_name, group_name, max(created) as created
from jars
where group_name = :group_id
group by group_name, jar_name) l
on j.jar_name = l.jar_name
and j.group_name = l.group_name
-- Find basic info for latest version
and j.created = l.created
-- Find the created ts for latest release
left join
(select jar_name, group_name, max(created) as created
from jars
where version not like '%-SNAPSHOT'
and group_name = :group_id
group by group_name, jar_name) r
on j.jar_name = r.jar_name
and j.group_name = r.group_name
-- Find version for latest release
left join
(select jar_name, group_name, version, created from jars
where group_name = :group_id
) as r2
on j.jar_name = r2.jar_name
and j.group_name = r2.group_name
and r.created = r2.created
where j.group_name = :group_id
order by j.group_name asc, j.jar_name asc;

--name: find-jars-information
select j.jar_name, j.group_name, homepage, description, user,
j.version as latest_version, r2.version as latest_release
from jars j
-- Find the latest version
join
(select jar_name, group_name, max(created) as created
from jars
where group_name = :group_id
and jar_name = :artifact_id
group by group_name, jar_name) l
on j.jar_name = l.jar_name
and j.group_name = l.group_name
-- Find basic info for latest version
and j.created = l.created
-- Find the created ts for latest release
left join
(select jar_name, group_name, max(created) as created
from jars
where version not like '%-SNAPSHOT'
and group_name = :group_id
and jar_name = :artifact_id
group by group_name, jar_name) r
on j.jar_name = r.jar_name
and j.group_name = r.group_name
-- Find version for latest release
left join
(select jar_name, group_name, version, created from jars
where group_name = :group_id
and jar_name = :artifact_id
) as r2
on j.jar_name = r2.jar_name
and j.group_name = r2.group_name
and r.created = r2.created
where j.group_name = :group_id
and j.jar_name = :artifact_id
order by j.group_name asc, j.jar_name asc;
            
--name: add-member!
INSERT INTO groups (name, user, added_by)
VALUES (:groupname, :username, :added_by);

--name: add-jar!
INSERT INTO jars (group_name, jar_name, version, user, created, description, homepage, authors)
VALUES (:groupname, :jarname, :version, :user, :created, :description, :homepage, :authors);

--name: promote!
UPDATE jars
SET promoted_at = :promoted_at
WHERE (
  group_name = :group_id
  AND
  jar_name = :artifact_id
  AND
  version = :version
);

--name: promoted
SELECT promoted_at
FROM jars
WHERE (
  group_name = :group_id
  AND
  jar_name = :artifact_id
  AND
  version = :version
);

--name: delete-groups-jars!
DELETE FROM jars
WHERE group_name = :group_id;

--name: delete-jars!
DELETE FROM jars
WHERE (
  group_name = :group_id
  AND
  jar_name = :jar_id
);

--name: delete-jar-version!
DELETE FROM jars
WHERE (
  group_name = :group_id
  AND
  jar_name = :jar_id
  AND
  version = :version
);

--name: delete-group!
DELETE FROM groups
WHERE name = :group_id;

--name: clear-groups!
DELETE FROM groups;

--name: clear-jars!
DELETE FROM jars;

--name: clear-users!
DELETE FROM users;
