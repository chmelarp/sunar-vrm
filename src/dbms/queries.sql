--
-- results (na kolika kamerach se objekt detekl)
--
select dataset, video, experiment, count(distinct camera), count(distinct track), count(*) from sunar.processed
where experiment>0 and object>0
group by dataset, video, experiment
order by count(distinct camera) desc, count(*) desc, count(track) desc, dataset, video

--
-- Sunar Cleaning (check and clean the data)
--

-- check finished videos
SELECT * FROM sunar.videos
ORDER BY dataset, camera, video;

-- check finished videos (according to track)
SELECT dataset, camera, video, count(track) FROM sunar.tracks
GROUP BY dataset, camera, video
ORDER BY dataset, camera, video;

-- assign datasets
UPDATE sunar.videos SET use='DEV09';
UPDATE sunar.videos SET use='EVAL09'
WHERE dataset=1 OR dataset=5 OR dataset=8 OR dataset=11;

-- TODO: update the video length according to the smallest length at all cameras!


-- select and delete unfinished videos
SELECT DISTINCT dataset, camera, video FROM sunar.states WHERE (dataset, camera, video) NOT IN (SELECT DISTINCT dataset, camera, video FROM sunar.videos);
DELETE FROM sunar.states WHERE (dataset, camera, video) NOT IN (SELECT DISTINCT dataset, camera, video FROM sunar.videos);

-- update video offsets
UPDATE sunar.videos set "offset"=sunar.video_offset(dataset, camera, video);
UPDATE sunar.tracks set "offset"=sunar.video_offset(dataset, camera, video);

-- DO NOT execute this UPDATE! Only the SunarCleaning knows how to update!
UPDATE ONLY sunar.tracks SET
 -- time, kalman X, kalman Y, Width, Height, kalman speedX*100, kalman speedY*100, kalman speed (dXY)
 -- kalman speed is NOT inverted for the purpose of i-Lids 2009 evaluation
 firsts = ARRAY[7701, 507, 152, 138, 183],
 -- time, position X, position Y, Width, Heigh
 lasts  = ARRAY[7515, 29, 431, 47, 111, -224, 1095, 1118],
 -- average of time, X, Y, W, H, speedX, speedY, speed (avg dXY := v=s/t)
 avgs   = ARRAY[93, 235, 259, 124, 190, 515, 264, 626],
 -- standard deviation of time, X, Y, W, H, speedX, speedY, speed (D of dXY ... a=v/t)
 stdevs = ARRAY[1, 148, 59, 29, 28, 212, 245, 217],
 -- sums of time, PIXEL distance X, Y, both (just for drunk detection)
 sums   = ARRAY[186, 1001, 614, 1289],
 -- avg and stdev of color (2x35)
 avgcolors = ARRAY[ 109, 143, 159, 148, 122, 156, 130, 132, 120, 124, 136, 144, 111, 144, 129, 128, 127, 124, 128, 138, 133, 129, 134, 136, 135, 124, 133, 137, 132, 130, 131, 134, 131, 131, 131],
 stdcolors = ARRAY[  17,  30,  20,  23,  16,  26,  25,  17,  28,  14,  12,  15,  17,  20,  24,   3,   6,   5,   6,   5,   5,   5,   5,   7,   4,   1,   3,   4,   3,   2,   2,   2,   3,   4,   3]
WHERE dataset=2 AND camera=1 AND video=1 AND track=175

-- check it (the update above)
SELECT t.dataset, t.video, t.camera, count(t.track), count(nnu.track)
FROM ONLY sunar.tracks AS t LEFT JOIN
     (SELECT dataset, video, camera, track FROM ONLY sunar.tracks WHERE avgs IS NULL) AS nnu
  ON t.dataset=nnu.dataset AND t.video=nnu.video AND t.camera=nnu.camera AND t.track=nnu.track
GROUP BY t.dataset, t.video, t.camera
ORDER BY t.dataset, t.video, t.camera




--
-- Sunar Integration (annotations)
--

-- select annotations (tracks)
SELECT antr.dataset, antr.object, antr.video, antr.firsts[1], antr.lasts[1], antr.camera, antr.track, vs.name
FROM ONLY sunar.annotation_tracks AS antr JOIN sunar.videos AS vs
     ON   antr.dataset=vs.dataset AND antr.camera=vs.camera AND antr.video=vs.video
ORDER BY dataset, object, video, firsts[1], lasts[1], camera, track;

-- select tracks concurrent to annotations (states) (position and size split because of libpq sucks)
SELECT st.time, st.position[0]::integer, st.position[1]::integer, st.size[0]::integer, st.size[1]::integer, st.track, sunar.overlaps(anst.position, anst.size, st.position, st.size)
 FROM ONLY sunar.states st LEFT JOIN ONLY sunar.annotation_states anst
   ON st.dataset=anst.dataset AND st.video=anst.video AND st.camera=anst.camera AND (anst.time = st.time OR anst.time = (st.time-1))
WHERE st.dataset=2 AND st.video=1 AND st.camera=2 AND st.time > (7775-25) AND st.time < (11650+25)
ORDER BY st.time

-- DEPRECATED: join extracted information from annotations and extractions (deprecated by the below)
SELECT  antr.object, anst.camera, anst.video, anst.track, st.track, anst.time, st.time, anst.position, st.position, anst.size, st.size, anst.occlusion, sunar.overlaps(anst.position, anst.size, st.position, st.size), st.color
FROM ONLY sunar.annotation_states AS anst, ONLY sunar.states AS st, ONLY sunar.annotation_tracks AS antr
WHERE anst.dataset = st.dataset AND anst.camera = st.camera AND anst.video = st.video AND (anst.time = st.time OR anst.time = (st.time+1))
  AND anst.dataset = antr.dataset AND anst.camera = antr.camera AND anst.video = antr.video AND anst.track = antr.track
  AND anst.dataset = 2 AND anst.dataset = 2 AND anst.camera=2
  AND sunar.overlaps(anst.position, anst.size, st.position, st.size) > 0.01
ORDER BY anst.time, anst.camera, anst.video, anst.track;

-- select annotation-processed information
SELECT ann.dataset, ann.object, ann.video, ann.camera, ann.track AS antrack, proc.track AS proctrack, ann."time", sunar.overlaps(ann.position, ann.size, proc.position, proc.size)
  FROM ONLY sunar.processed AS proc JOIN ONLY sunar.annotations AS ann
    ON (ann.dataset = proc.dataset AND ann.video = proc.video AND ann.camera = proc.camera AND (ann.time = proc.time OR ann.time = (proc.time+1)))
 WHERE sunar.overlaps(ann.position, ann.size, proc.position, proc.size) > 0.05
       AND ann.dataset = 2 AND ann.camera=1 AND ann.video=1
 ORDER BY ann.dataset, ann.video, ann.camera, ann.track, ann.time;

-- select summarized annotation-processed information (to prve orechove :)
SELECT ann.dataset, ann.object, ann.video, ann.camera, ann.track AS antrack, proc.track AS proctrack, sum(sunar.overlaps(ann.position, ann.size, proc.position, proc.size)), min(proc.time), max(proc.time)
  FROM ONLY sunar.processed AS proc JOIN ONLY sunar.annotations AS ann
    ON (ann.dataset = proc.dataset AND ann.video = proc.video AND ann.camera = proc.camera AND (ann.time = proc.time OR ann.time = (proc.time+1)))
 WHERE sunar.overlaps(ann.position, ann.size, proc.position, proc.size) > 0.05
--     AND ann.dataset = 2 AND ann.video=1
 GROUP BY ann.dataset, ann.object, ann.video, ann.camera, ann.track, proc.track
 ORDER BY ann.dataset ASC, ann.video ASC, ann.camera ASC, ann.track ASC, sum(sunar.overlaps(ann.position, ann.size, proc.position, proc.size)) DESC;

-- update - don't know how to do it :(

--
-- Sunar Integration (training)
--
SELECT h.camera1, count(*)
  FROM sunar.annotation_handovers h JOIN sunar.videos v
    ON (h.dataset=v.dataset AND h.video=v.video AND h.camera1=v.camera)
 WHERE v.use LIKE 'DEV09'
 GROUP BY h.camera1
 ORDER BY h.camera1;


--
-- Sunar Integration (experiments)
--

-- files to be generated
SELECT ex.track,  tr.dataset, tr.camera, tr.video, tr."object", ex.tracking_trial_id, ex.exp_sysid, ex."version", ex."type"
  FROM sunar.evaluation_experiments AS ex
  JOIN sunar.evaluation_tracks AS tr
    ON (ex.track=tr.track)
 WHERE ex.track=1;

-- takhle se priradi processed k evaluations (pro dry run)
SELECT ev.dataset, ev.object, ev.video, ev.camera, ev.track AS evtrack, proc.track AS proctrack, proc."object", ev."time"
  FROM ONLY sunar.processed AS proc JOIN ONLY sunar.evaluations AS ev
    ON (ev.dataset = proc.dataset AND ev.video = proc.video AND ev.camera = proc.camera AND (ev.time = proc.time OR ev.time = (proc.time+1)))
 WHERE proc."object" IS NOT NULL AND ev.track=1
 ORDER BY ev.dataset, ev.video, ev.camera, ev.track, ev.time;

-- takhle se udela view/tabulka handoveru
 -- DROP TABLE IF EXISTS sunar.annotation_handovers
 SELECT ft.dataset, ft.video, ft.object, ft.camera AS camera1, st.camera AS camera2,
        ft.track AS track1, st.track AS track2, ft.firsts AS firsts1, st.firsts AS firsts2,
        ft.lasts AS lasts1, st.lasts AS lasts2, st.firsts[1] - ft.lasts[1] AS delta_t,
        ft.avgs AS avgs1, st.avgs AS avgs2, ft.stdevs AS stdevs1, st.stdevs AS stdevs2,
        ft.sums AS sums1, st.sums AS sums2, ft.avgcolors AS avgcolors1, st.avgcolors AS avgcolors2,
        ft.stdcolors AS stdcolors1, st.stdcolors AS stdcolors2,
        vector_minus_int4(ft.avgcolors, st.avgcolors) AS delta_color,
        sqrt(distance_square_int4(ft.avgcolors, st.avgcolors)::double precision) AS delta_color_dist
   INTO sunar.annotation_handovers
   FROM ONLY sunar.tracks ft
   JOIN ONLY sunar.tracks st ON ft.dataset = st.dataset AND ft.object = st.object AND ft.video = st.video AND st.firsts[1] > ft.firsts[1]
  WHERE ft.object IS NOT NULL AND ft.object > 0 AND st.firsts[1] = (sunar.track_follows(ft.dataset, ft.video, ft.object, ft.firsts[1])).firsts[1]
        AND delta_t > -750 AND delta_t < 1125
--  ORDER BY ft.camera, st.camera, ft.dataset, ft.video, ft.firsts[1];
  ALTER TABLE sunar.annotation_handovers ADD CONSTRAINT annotation_handovers_pk PRIMARY KEY (dataset, video, object, camera1, camera2, track1, track2);
  ALTER TABLE sunar.annotation_handovers OWNER TO chmelarp;
  GRANT ALL ON TABLE sunar.annotation_handovers TO public;
-- ale bacha na to vyse... je to huste neoptimalni a navic nemezpecne:
COMMENT ON VIEW sunar.handovers IS 'Warning! May fail if an object appered in more than 1 comera at the same moment!';
-- ale tabulka je lepsi, protoze umoznije smazat kraviny (prechod 3-1 za -1000f)


-- just FYI (stats about handovers)
SELECT camera1, camera2, count(*) AS samples, avg(delta_t) AS avg_delta_t, min(delta_t) AS min_delta_t, max(delta_t) AS max_delta_t,
       avg(delta_color_dist) AS avg_delta_color_dist, min(delta_color_dist) AS min_delta_color_dist, max(delta_color_dist) AS max_delta_color_dist
  FROM sunar.training_handovers_cache
 GROUP BY camera1, camera2
 ORDER BY camera1, camera2;
-- TODO: see and delete unappropriate ones... (SELECT * FROM sunar.handovers AS sunar.annotatnion_handovers)


DROP TABLE IF EXISTS sunar.training_handovers_cache;

CREATE OR REPLACE VIEW sunar.training_cameras AS
 SELECT h.camera1, count(*) AS annotated_trakcs
   FROM sunar.training_handovers_cache h
   JOIN sunar.videos v ON h.dataset = v.dataset AND h.video = v.video AND h.camera1 = v.camera
  WHERE v.use::text ~~ 'DEV09'::text
  GROUP BY h.camera1
  ORDER BY h.camera1;

ALTER TABLE sunar.training_cameras OWNER TO chmelarp;
GRANT ALL ON TABLE sunar.training_cameras TO chmelarp;
GRANT ALL ON TABLE sunar.training_cameras TO public;
