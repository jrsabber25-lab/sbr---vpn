from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    jwt_secret: str = "DEV_ONLY_REPLACE_WITH_OPENSSL_RAND_HEX_32"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 1440
    database_url: str = "sqlite+aiosqlite:///./sbr_vpn.db"
    node_agent_secret: str = "DEV_ONLY_NODE_AGENT_SECRET"
    bootstrap_admin_email: str = "admin@sbrvpn.example.com"
    bootstrap_admin_password: str = ""
    cors_origins: str = "http://localhost:8080,http://127.0.0.1:8080"

    class Config:
        env_prefix = "SBR_"
        env_file = ".env"


settings = Settings()
