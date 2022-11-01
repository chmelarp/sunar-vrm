--
-- PostgreSQL database dump
--

-- Started on 2009-07-21 14:43:13 CEST

SET client_encoding = 'UTF8';
SET standard_conforming_strings = off;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET escape_string_warning = off;

--
-- TOC entry 7 (class 2615 OID 16386)
-- Name: sunar; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA sunar;


SET search_path = sunar, pg_catalog;

--
-- TOC entry 22 (class 1255 OID 16434)
-- Dependencies: 344 7
-- Name: overlaps(point, point, point, point); Type: FUNCTION; Schema: sunar; Owner: -
--

CREATE FUNCTION "overlaps"(pos1 point, size1 point, pos2 point, size2 point) RETURNS real
    AS $$

-- Returns ratio of intersection to union of two rectangles.
-- Usage: SELECT sunar.overlaps('(2,2)'::POINT, '(2,2)'::POINT, '(2,3)'::POINT, '(2,2)'::POINT);

DECLARE
	x1 REAL;
	x2 REAL;
	y1 REAL;
	y2 REAL;
	xd REAL;
	yd REAL;
	s1 REAL;
	s2 REAL;
	su REAL;
        si REAL;

BEGIN
	-- count 4 distances
	x1 := (pos1[0]+size1[0]/2) - (pos2[0]-size2[0]/2);
	x2 := (pos2[0]+size2[0]/2) - (pos1[0]-size1[0]/2);
	y1 := (pos1[1]+size1[1]/2) - (pos2[1]-size2[1]/2);
	y2 := (pos2[1]+size2[1]/2) - (pos1[1]-size1[1]/2);

	-- if it doesnt overlap, return 0
	IF (x1 <= 0) OR (x2 <= 0) OR (y1 <= 0) OR (y2 <= 0) THEN
		RETURN 0;
	END IF;

	-- count intersection to union ratio
	-- idea: correlation = (A1 * A2) / (A1 + A2) = SI / SU

	-- count square of intersection
	IF (x1 < x2) THEN xd := x1;
	ELSE xd := x2;
	END IF;

	IF (y1 < y2) THEN yd := y1;
	ELSE yd := y2;
	END IF;

	-- fix if in center (xd/yd)
	IF (xd > size1[0]) THEN xd := size1[0];
	ELSE IF (xd > size2[0]) THEN xd := size2[0];
	END IF; END IF;

	IF (yd > size1[1]) THEN yd := size1[1];
	ELSE IF (yd > size2[1]) THEN yd := size2[1];
	END IF; END IF;

	si := xd*yd;

	-- count square of union
	s1 := (size1[0]*size1[1]); 
	s2 := (size2[0]*size2[1]);
	su := s1 + s2 - si;
	
	-- return the ratio of squares
	RETURN si / su;
END;
$$
    LANGUAGE plpgsql STRICT;


SET default_tablespace = '';

SET default_with_oids = false;

--
-- TOC entry 1518 (class 1259 OID 16402)
-- Dependencies: 7
-- Name: tracks; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE tracks (
    dataset integer NOT NULL,
    camera integer NOT NULL,
    video integer NOT NULL,
    track integer NOT NULL,
    object integer,
    "offset" integer,
    firsts integer[],
    lasts integer[],
    avgs integer[],
    stdevs integer[],
    sums integer[],
    avgcolors integer[],
    stdcolors integer[]
);


--
-- TOC entry 32 (class 1255 OID 16472)
-- Dependencies: 7 313 344
-- Name: track_follows(integer, integer, integer); Type: FUNCTION; Schema: sunar; Owner: -
--

CREATE FUNCTION track_follows(dst integer, obj integer, tim integer) RETURNS tracks
    AS $$
DECLARE
	fol sunar.tracks;
BEGIN
	SELECT * INTO fol
	FROM ONLY sunar.tracks
	WHERE "offset" + firsts[1] > tim AND dataset=dst AND "object"=obj
	ORDER BY "offset" + firsts[1]
	LIMIT 1;

	RETURN fol;
END;
$$
    LANGUAGE plpgsql STRICT COST 1000;


--
-- TOC entry 34 (class 1255 OID 16481)
-- Dependencies: 313 7 344
-- Name: track_follows(integer, integer, integer, integer); Type: FUNCTION; Schema: sunar; Owner: -
--

CREATE FUNCTION track_follows(dst integer, vid integer, obj integer, tim integer) RETURNS tracks
    AS $$
DECLARE
	fol sunar.tracks;
BEGIN
	SELECT * INTO fol
	FROM ONLY sunar.tracks
	WHERE firsts[1] > tim AND dataset=dst AND video=vid AND "object"=obj
	ORDER BY firsts[1]
	LIMIT 1;

	RETURN fol;
END;
$$
    LANGUAGE plpgsql STRICT COST 1000;


--
-- TOC entry 21 (class 1255 OID 16435)
-- Dependencies: 344 7
-- Name: video_offset(integer, integer, integer); Type: FUNCTION; Schema: sunar; Owner: -
--

CREATE FUNCTION video_offset(dst integer, cmr integer, vdo integer) RETURNS integer
    AS $$
DECLARE
	su INTEGER;
BEGIN
	SELECT sum(length), dataset, camera  INTO su    -- omit others... checked by count(videos)
	FROM sunar.videos
	WHERE dataset=dst AND CAMERA=cmr AND video < vdo
	GROUP BY dataset, camera;

	IF (su IS NULL) THEN RETURN 0;
	END IF;
	RETURN su;

END;
$$
    LANGUAGE plpgsql STRICT;


--
-- TOC entry 1529 (class 1259 OID 82038)
-- Dependencies: 7
-- Name: annotation_handovers; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE annotation_handovers (
    dataset integer NOT NULL,
    video integer NOT NULL,
    object integer NOT NULL,
    camera1 integer NOT NULL,
    camera2 integer NOT NULL,
    track1 integer NOT NULL,
    track2 integer NOT NULL,
    firsts1 integer[],
    firsts2 integer[],
    lasts1 integer[],
    lasts2 integer[],
    delta_t integer,
    avgs1 integer[],
    avgs2 integer[],
    stdevs1 integer[],
    stdevs2 integer[],
    sums1 integer[],
    sums2 integer[],
    avgcolors1 integer[],
    avgcolors2 integer[],
    stdcolors1 integer[],
    stdcolors2 integer[],
    delta_color integer[],
    delta_color_dist double precision
);


--
-- TOC entry 1516 (class 1259 OID 16388)
-- Dependencies: 1802 7
-- Name: states; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE states (
    dataset integer NOT NULL,
    camera integer NOT NULL,
    video integer NOT NULL,
    track bigint NOT NULL,
    "time" integer NOT NULL,
    "position" point,
    size point,
    color integer[],
    occlusion boolean DEFAULT false
);


--
-- TOC entry 1517 (class 1259 OID 16395)
-- Dependencies: 1803 7 1516
-- Name: annotation_states; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE annotation_states (
)
INHERITS (states);


--
-- TOC entry 1519 (class 1259 OID 16408)
-- Dependencies: 7 1518
-- Name: annotation_tracks; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE annotation_tracks (
)
INHERITS (tracks);


--
-- TOC entry 1520 (class 1259 OID 16414)
-- Dependencies: 1607 7
-- Name: annotations; Type: VIEW; Schema: sunar; Owner: -
--

CREATE VIEW annotations AS
    SELECT atr.dataset, atr.object, atr.video, atr.camera, atr.track, atr."offset", ast."time", ast."position", ast.size FROM (ONLY annotation_tracks atr JOIN ONLY annotation_states ast ON (((((ast.dataset = atr.dataset) AND (ast.video = atr.video)) AND (ast.camera = atr.camera)) AND (ast.track = atr.track))));


--
-- TOC entry 1530 (class 1259 OID 82046)
-- Dependencies: 7
-- Name: evaluation_experiments_seq; Type: SEQUENCE; Schema: sunar; Owner: -
--

CREATE SEQUENCE evaluation_experiments_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


--
-- TOC entry 1531 (class 1259 OID 82052)
-- Dependencies: 1806 1807 1808 1809 7
-- Name: evaluation_experiments; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE evaluation_experiments (
    track integer DEFAULT nextval('evaluation_experiments_seq'::regclass) NOT NULL,
    tracking_trial_id character varying(100) NOT NULL,
    ecf_filename character varying(100) NOT NULL,
    exp_sysid character varying(100) DEFAULT 'p-Sunar'::character varying NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    type character varying(40) DEFAULT 'MCSPT'::character varying NOT NULL
);


--
-- TOC entry 1525 (class 1259 OID 16448)
-- Dependencies: 1805 1516 7
-- Name: evaluation_states; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE evaluation_states (
)
INHERITS (states);


--
-- TOC entry 1526 (class 1259 OID 16457)
-- Dependencies: 1518 7
-- Name: evaluation_tracks; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE evaluation_tracks (
)
INHERITS (tracks);


--
-- TOC entry 1527 (class 1259 OID 16465)
-- Dependencies: 1609 7
-- Name: evaluations; Type: VIEW; Schema: sunar; Owner: -
--

CREATE VIEW evaluations AS
    SELECT etr.dataset, etr.object, etr.video, etr.camera, etr.track, etr."offset", est."time", est."position", est.size FROM (ONLY evaluation_tracks etr JOIN ONLY evaluation_states est ON (((((est.dataset = etr.dataset) AND (est.video = etr.video)) AND (est.camera = etr.camera)) AND (est.track = etr.track))));


--
-- TOC entry 1528 (class 1259 OID 82023)
-- Dependencies: 1610 7
-- Name: handovers; Type: VIEW; Schema: sunar; Owner: -
--

CREATE VIEW handovers AS
    SELECT ft.dataset, ft.video, ft.object, ft.camera AS camera1, st.camera AS camera2, ft.track AS track1, st.track AS track2, ft.firsts AS firsts1, st.firsts AS firsts2, ft.lasts AS lasts1, st.lasts AS lasts2, (st.firsts[1] - ft.lasts[1]) AS delta_t, ft.avgs AS avgs1, st.avgs AS avgs2, ft.stdevs AS stdevs1, st.stdevs AS stdevs2, ft.sums AS sums1, st.sums AS sums2, ft.avgcolors AS avgcolors1, st.avgcolors AS avgcolors2, ft.stdcolors AS stdcolors1, st.stdcolors AS stdcolors2, public.vector_minus_int4(ft.avgcolors, st.avgcolors) AS delta_color, sqrt((public.distance_square_int4(ft.avgcolors, st.avgcolors))::double precision) AS delta_color_dist FROM (ONLY tracks ft JOIN ONLY tracks st ON (((((ft.dataset = st.dataset) AND (ft.object = st.object)) AND (ft.video = st.video)) AND (st.firsts[1] > ft.firsts[1])))) WHERE (((ft.object IS NOT NULL) AND (ft.object > 0)) AND (st.firsts[1] = (track_follows(ft.dataset, ft.video, ft.object, ft.firsts[1])).firsts[1])) ORDER BY ft.dataset, ft.video, ft.firsts[1];


--
-- TOC entry 1844 (class 0 OID 0)
-- Dependencies: 1528
-- Name: VIEW handovers; Type: COMMENT; Schema: sunar; Owner: -
--

COMMENT ON VIEW handovers IS 'Warning! May fail if an object appered in more than 1 comera at the same moment!';


--
-- TOC entry 1521 (class 1259 OID 16418)
-- Dependencies: 7
-- Name: obj_seq; Type: SEQUENCE; Schema: sunar; Owner: -
--

CREATE SEQUENCE obj_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


--
-- TOC entry 1522 (class 1259 OID 16420)
-- Dependencies: 1804 7
-- Name: objects; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE objects (
    dataset integer NOT NULL,
    object integer DEFAULT nextval('obj_seq'::regclass) NOT NULL,
    verified boolean
);


--
-- TOC entry 1523 (class 1259 OID 16424)
-- Dependencies: 1608 7
-- Name: processed; Type: VIEW; Schema: sunar; Owner: -
--

CREATE VIEW processed AS
    SELECT tr.dataset, tr.object, tr.video, tr.camera, tr.track, tr."offset", st."time", st."position", st.size, st.color FROM (ONLY tracks tr JOIN ONLY states st ON (((((st.dataset = tr.dataset) AND (st.video = tr.video)) AND (st.camera = tr.camera)) AND (st.track = tr.track))));


--
-- TOC entry 1524 (class 1259 OID 16428)
-- Dependencies: 7
-- Name: videos; Type: TABLE; Schema: sunar; Owner: -; Tablespace: 
--

CREATE TABLE videos (
    dataset integer NOT NULL,
    camera integer NOT NULL,
    video integer NOT NULL,
    name character varying(400),
    "offset" integer,
    length integer,
    fps integer
);


--
-- TOC entry 1825 (class 2606 OID 82045)
-- Dependencies: 1529 1529 1529 1529 1529 1529 1529 1529
-- Name: annotation_handovers_pk; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY annotation_handovers
    ADD CONSTRAINT annotation_handovers_pk PRIMARY KEY (dataset, video, object, camera1, camera2, track1, track2);


--
-- TOC entry 1815 (class 2606 OID 16439)
-- Dependencies: 1519 1519 1519 1519 1519
-- Name: annotations_id; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY annotation_tracks
    ADD CONSTRAINT annotations_id PRIMARY KEY (dataset, camera, video, track);


--
-- TOC entry 1827 (class 2606 OID 82057)
-- Dependencies: 1531 1531
-- Name: evaluation_experiments_pk; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY evaluation_experiments
    ADD CONSTRAINT evaluation_experiments_pk PRIMARY KEY (track);


--
-- TOC entry 1821 (class 2606 OID 16456)
-- Dependencies: 1525 1525 1525 1525 1525 1525
-- Name: evaluation_states_pk; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY evaluation_states
    ADD CONSTRAINT evaluation_states_pk PRIMARY KEY (dataset, camera, video, track, "time");


--
-- TOC entry 1823 (class 2606 OID 16464)
-- Dependencies: 1526 1526 1526 1526 1526
-- Name: evaluation_track_pk; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY evaluation_tracks
    ADD CONSTRAINT evaluation_track_pk PRIMARY KEY (dataset, camera, video, track);


--
-- TOC entry 1817 (class 2606 OID 16441)
-- Dependencies: 1522 1522
-- Name: object_id; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY objects
    ADD CONSTRAINT object_id PRIMARY KEY (object);


--
-- TOC entry 1811 (class 2606 OID 16443)
-- Dependencies: 1516 1516 1516 1516 1516 1516
-- Name: state_id; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY states
    ADD CONSTRAINT state_id PRIMARY KEY (dataset, camera, video, track, "time");


--
-- TOC entry 1813 (class 2606 OID 16445)
-- Dependencies: 1518 1518 1518 1518 1518
-- Name: track_id; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY tracks
    ADD CONSTRAINT track_id PRIMARY KEY (dataset, camera, video, track);


--
-- TOC entry 1819 (class 2606 OID 16447)
-- Dependencies: 1524 1524 1524 1524
-- Name: videos_pk; Type: CONSTRAINT; Schema: sunar; Owner: -; Tablespace: 
--

ALTER TABLE ONLY videos
    ADD CONSTRAINT videos_pk PRIMARY KEY (dataset, camera, video);


--
-- TOC entry 1830 (class 0 OID 0)
-- Dependencies: 7
-- Name: sunar; Type: ACL; Schema: -; Owner: -
--

REVOKE ALL ON SCHEMA sunar FROM PUBLIC;
REVOKE ALL ON SCHEMA sunar FROM chmelarp;
GRANT ALL ON SCHEMA sunar TO chmelarp;
GRANT ALL ON SCHEMA sunar TO PUBLIC;


--
-- TOC entry 1831 (class 0 OID 0)
-- Dependencies: 22
-- Name: overlaps(point, point, point, point); Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON FUNCTION "overlaps"(pos1 point, size1 point, pos2 point, size2 point) FROM PUBLIC;
REVOKE ALL ON FUNCTION "overlaps"(pos1 point, size1 point, pos2 point, size2 point) FROM chmelarp;
GRANT ALL ON FUNCTION "overlaps"(pos1 point, size1 point, pos2 point, size2 point) TO chmelarp;
GRANT ALL ON FUNCTION "overlaps"(pos1 point, size1 point, pos2 point, size2 point) TO PUBLIC;


--
-- TOC entry 1832 (class 0 OID 0)
-- Dependencies: 1518
-- Name: tracks; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE tracks FROM PUBLIC;
REVOKE ALL ON TABLE tracks FROM chmelarp;
GRANT ALL ON TABLE tracks TO chmelarp;
GRANT ALL ON TABLE tracks TO PUBLIC;


--
-- TOC entry 1833 (class 0 OID 0)
-- Dependencies: 21
-- Name: video_offset(integer, integer, integer); Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON FUNCTION video_offset(dst integer, cmr integer, vdo integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION video_offset(dst integer, cmr integer, vdo integer) FROM chmelarp;
GRANT ALL ON FUNCTION video_offset(dst integer, cmr integer, vdo integer) TO chmelarp;
GRANT ALL ON FUNCTION video_offset(dst integer, cmr integer, vdo integer) TO PUBLIC;


--
-- TOC entry 1834 (class 0 OID 0)
-- Dependencies: 1529
-- Name: annotation_handovers; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE annotation_handovers FROM PUBLIC;
REVOKE ALL ON TABLE annotation_handovers FROM chmelarp;
GRANT ALL ON TABLE annotation_handovers TO chmelarp;
GRANT ALL ON TABLE annotation_handovers TO PUBLIC;


--
-- TOC entry 1835 (class 0 OID 0)
-- Dependencies: 1516
-- Name: states; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE states FROM PUBLIC;
REVOKE ALL ON TABLE states FROM chmelarp;
GRANT ALL ON TABLE states TO chmelarp;
GRANT ALL ON TABLE states TO PUBLIC;


--
-- TOC entry 1836 (class 0 OID 0)
-- Dependencies: 1517
-- Name: annotation_states; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE annotation_states FROM PUBLIC;
REVOKE ALL ON TABLE annotation_states FROM chmelarp;
GRANT ALL ON TABLE annotation_states TO chmelarp;
GRANT ALL ON TABLE annotation_states TO PUBLIC;


--
-- TOC entry 1837 (class 0 OID 0)
-- Dependencies: 1519
-- Name: annotation_tracks; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE annotation_tracks FROM PUBLIC;
REVOKE ALL ON TABLE annotation_tracks FROM chmelarp;
GRANT ALL ON TABLE annotation_tracks TO chmelarp;
GRANT ALL ON TABLE annotation_tracks TO PUBLIC;


--
-- TOC entry 1838 (class 0 OID 0)
-- Dependencies: 1520
-- Name: annotations; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE annotations FROM PUBLIC;
REVOKE ALL ON TABLE annotations FROM chmelarp;
GRANT ALL ON TABLE annotations TO chmelarp;
GRANT ALL ON TABLE annotations TO PUBLIC;


--
-- TOC entry 1839 (class 0 OID 0)
-- Dependencies: 1530
-- Name: evaluation_experiments_seq; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON SEQUENCE evaluation_experiments_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE evaluation_experiments_seq FROM chmelarp;
GRANT ALL ON SEQUENCE evaluation_experiments_seq TO chmelarp;
GRANT ALL ON SEQUENCE evaluation_experiments_seq TO PUBLIC;


--
-- TOC entry 1840 (class 0 OID 0)
-- Dependencies: 1531
-- Name: evaluation_experiments; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE evaluation_experiments FROM PUBLIC;
REVOKE ALL ON TABLE evaluation_experiments FROM chmelarp;
GRANT ALL ON TABLE evaluation_experiments TO chmelarp;
GRANT ALL ON TABLE evaluation_experiments TO PUBLIC;


--
-- TOC entry 1841 (class 0 OID 0)
-- Dependencies: 1525
-- Name: evaluation_states; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE evaluation_states FROM PUBLIC;
REVOKE ALL ON TABLE evaluation_states FROM chmelarp;
GRANT ALL ON TABLE evaluation_states TO chmelarp;
GRANT ALL ON TABLE evaluation_states TO PUBLIC;


--
-- TOC entry 1842 (class 0 OID 0)
-- Dependencies: 1526
-- Name: evaluation_tracks; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE evaluation_tracks FROM PUBLIC;
REVOKE ALL ON TABLE evaluation_tracks FROM chmelarp;
GRANT ALL ON TABLE evaluation_tracks TO chmelarp;
GRANT ALL ON TABLE evaluation_tracks TO PUBLIC;


--
-- TOC entry 1843 (class 0 OID 0)
-- Dependencies: 1527
-- Name: evaluations; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE evaluations FROM PUBLIC;
REVOKE ALL ON TABLE evaluations FROM chmelarp;
GRANT ALL ON TABLE evaluations TO chmelarp;
GRANT ALL ON TABLE evaluations TO PUBLIC;


--
-- TOC entry 1845 (class 0 OID 0)
-- Dependencies: 1528
-- Name: handovers; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE handovers FROM PUBLIC;
REVOKE ALL ON TABLE handovers FROM chmelarp;
GRANT ALL ON TABLE handovers TO chmelarp;
GRANT ALL ON TABLE handovers TO postgres;
GRANT ALL ON TABLE handovers TO PUBLIC;


--
-- TOC entry 1846 (class 0 OID 0)
-- Dependencies: 1521
-- Name: obj_seq; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON SEQUENCE obj_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE obj_seq FROM chmelarp;
GRANT ALL ON SEQUENCE obj_seq TO chmelarp;
GRANT ALL ON SEQUENCE obj_seq TO PUBLIC;


--
-- TOC entry 1847 (class 0 OID 0)
-- Dependencies: 1522
-- Name: objects; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE objects FROM PUBLIC;
REVOKE ALL ON TABLE objects FROM chmelarp;
GRANT ALL ON TABLE objects TO chmelarp;
GRANT ALL ON TABLE objects TO PUBLIC;


--
-- TOC entry 1848 (class 0 OID 0)
-- Dependencies: 1523
-- Name: processed; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE processed FROM PUBLIC;
REVOKE ALL ON TABLE processed FROM chmelarp;
GRANT ALL ON TABLE processed TO chmelarp;
GRANT ALL ON TABLE processed TO PUBLIC;


--
-- TOC entry 1849 (class 0 OID 0)
-- Dependencies: 1524
-- Name: videos; Type: ACL; Schema: sunar; Owner: -
--

REVOKE ALL ON TABLE videos FROM PUBLIC;
REVOKE ALL ON TABLE videos FROM chmelarp;
GRANT ALL ON TABLE videos TO chmelarp;
GRANT ALL ON TABLE videos TO trecvid;
GRANT ALL ON TABLE videos TO PUBLIC;


-- Completed on 2009-07-21 14:43:14 CEST

--
-- PostgreSQL database dump complete
--

