Rails.application.routes.draw do
  get '/health', to: ->(env) {
    [200, { 'Content-Type' => 'application/json' }, ['{"status":"ok"}']]
  }
end
