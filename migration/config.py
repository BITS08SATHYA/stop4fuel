"""Database connection configuration for migration."""
import os
from dotenv import load_dotenv

load_dotenv()

MYSQL_CONFIG = {
    'host': os.getenv('MYSQL_HOST', '127.0.0.1'),
    'port': int(os.getenv('MYSQL_PORT', '3306')),
    'user': os.getenv('MYSQL_USER', 'root'),
    'password': os.getenv('MYSQL_PASSWORD', 'mypassword'),
    'database': os.getenv('MYSQL_DATABASE', 'bunkdb'),
    'charset': 'utf8',
    'use_unicode': True,
}

PG_CONFIG = {
    'host': os.getenv('PG_HOST', '127.0.0.1'),
    'port': int(os.getenv('PG_PORT', '5432')),
    'user': os.getenv('PG_USER', 'postgres'),
    'password': os.getenv('PG_PASSWORD', 'myPassword'),
    'dbname': os.getenv('PG_DATABASE', 'stopforfuel'),
}

# All migrated records use this tenant ID
SCID = 1
