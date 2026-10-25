
import './App.css'

function App() {
  const features = [
    {
      icon: '📄',
      title: 'Document Management',
      description:
        'Upload, organize, and manage your documents in one secure workspace.',
    },
    {
      icon: '🧠',
      title: 'AI-Powered Questions',
      description:
        'Ask questions using natural language and discover relevant information.',
    },
    {
      icon: '📊',
      title: 'Document Analytics',
      description:
        'Explore document usage, important topics, and knowledge gaps.',
    },
  ]

  return (
    <div className="app">

      {/* Navigation bar */}
      <header className="navbar">
        <a href="#home" className="brand">
          <span className="brand-icon">D</span>
          <span>Document Analytics Hub</span>
        </a>

        <nav className="nav-links" aria-label="Main navigation">
          <a href="#home">Home</a>
          <a href="#features">Features</a>
          <a href="#workflow">How It Works</a>
        </nav>

        <button
          className="btn btn-outline"
          type="button"
          disabled
          title="Authentication will be implemented soon"
        >
          Sign In (Soon)
        </button>
      </header>

      <main>
        {/* Hero section */}
        <section id="home" className="hero">
          <div className="hero-content">
            <span className="tag">RAG-Powered Document Intelligence</span>

            <h1>
              Understand Your Documents
              <span className="highlight"> With AI</span>
            </h1>

            <p className="hero-description">
              Transform complex documents into useful knowledge.
              Search, summarize, and get evidence-based answers
              using intelligent document analysis.
            </p>

            <div className="hero-actions">
              <a href="#features" className="btn btn-primary">
                Explore Features
              </a>
              <a href="#workflow" className="btn btn-outline">
                How It Works
              </a>
            </div>
          </div>

          {/* Sample answer card */}
          <div className="preview-card">
            <span className="preview-label">Example AI Response</span>

            <p className="preview-question">
              What is Retrieval-Augmented Generation?
            </p>

            <div className="answer-box">
              <strong>AI Answer</strong>
              <p>
                RAG helps an AI answer questions by first finding
                relevant information from trusted documents.
              </p>
            </div>

            <p className="source-text">
              📄 Example source: AI_Research.pdf — Page 12
            </p>
          </div>
        </section>

        {/* Features section */}
        <section id="features" className="features-section">
          <h2>Powerful Document Intelligence</h2>

          <p className="section-description">
            Everything you need to manage and understand documents.
          </p>

          <div className="feature-grid">
            {features.map((feature) => (
              <article className="feature-card" key={feature.title}>
                <div className="feature-icon">{feature.icon}</div>
                <h3>{feature.title}</h3>
                <p>{feature.description}</p>
              </article>
            ))}
          </div>
        </section>

        {/* Workflow section */}
        <section id="workflow" className="workflow-section">
          <h2>How It Works</h2>

          <div className="workflow-grid">
            <div className="workflow-step">
              <span>01</span>
              <h3>Upload</h3>
              <p>Add your PDF or document.</p>
            </div>

            <div className="workflow-step">
              <span>02</span>
              <h3>Process</h3>
              <p>AI indexes the document content.</p>
            </div>

            <div className="workflow-step">
              <span>03</span>
              <h3>Ask</h3>
              <p>Ask questions in natural language.</p>
            </div>

            <div className="workflow-step">
              <span>04</span>
              <h3>Understand</h3>
              <p>Receive answers with source references.</p>
            </div>
          </div>
        </section>
      </main>

      <footer className="footer">
        Intelligent Document Analytics Hub — RAG SaaS Project
      </footer>

    </div>
  )
}

export default App
