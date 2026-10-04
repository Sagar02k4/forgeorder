CREATE TABLE processed_messages (
                                    consumer_name VARCHAR(100) NOT NULL,
                                    event_id UUID NOT NULL,
                                    processed_at TIMESTAMPTZ NOT NULL,
                                    PRIMARY KEY (consumer_name, event_id)
);